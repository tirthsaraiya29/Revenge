# Clean-Room Specification — `core-db` cluster (`:core` persistence layer)

> **Required behaviour only. Any algorithm shape is illustrative; implementers must design
> independently and are audited for baseline similarity.**

Status: M2 specification (behavioural contract only).
Module: `:core`, Gradle path `:core`, directory `core`.
Cluster scope: everything under `core/src/main/kotlin/com/revenge/music/db` plus the exported Room
schemas in `core/schemas/com.revenge.music.db.InternalDatabase`.

This document describes observable behaviour, API shape and the physical database schema. It
deliberately omits statement-level implementation. Any implementation that satisfies the
contracts and acceptance tests is acceptable.

**Upgrade compatibility is a hard requirement.** Table names, column names, column order where
SQLite `CREATE TABLE` exposes it, primary keys, indices, foreign keys, view definitions, database
file name, database version (48) and every migration id listed in Section 7 must be preserved.
A shipped `song.db` from any prior version must open and migrate without data loss.

---

## 1. Module build contract

`core/build.gradle.kts` must:

- apply `com.android.library`, `com.google.devtools.ksp`, and the Kotlin serialization plugin;
- set `namespace = "com.revenge.core"`, `compileSdk = 37`, `minSdk = 26`;
- compile with Java/Kotlin toolchain 21 and core-library desugaring enabled
  (`java.time` is available from minSdk 26 regardless);
- make available at `api` scope: Room runtime + room-ktx, compose runtime/ui/animation, datastore,
  Timber, Ktor client core + serialization-json, Apache Commons Lang3 (`RandomStringUtils`),
  protobuf-javalite, media3 + media3-session, `javax.inject:javax.inject:1`, core-ktx;
- depend on `api(project(":innertube"))`, `api(project(":metadata"))`, `api(project(":domain"))`;
- run the Room compiler through KSP with the schema output directory set to
  `"$projectDir/schemas"`;
- provide `testImplementation` JUnit, kotlinx-coroutines-test, Robolectric.

Room must run in **KSP** mode. `exportSchema = true` is mandatory; the regenerated schema for
version 48 must be byte-equivalent in content to the checked-in `48.json` and carry the same
identity hash (`04eaba22573a4ef29ffee7f776038a84`). If entity declarations differ in any way Room
computes a new identity hash and the shipped schemas no longer validate.

---

## 2. Module scope and required public surface

Package root `com.revenge.music.db`. Exact file layout within the module is implementation-defined;
the following public types and functions, with these names and packages, are the API contract:

| Package | Required public surface |
|---|---|
| `com.revenge.music.db` | `Converters` (§3); `InternalDatabase` (§6.1); `MusicDatabase` facade (§6.2); `DatabaseDao` (§8). |
| `com.revenge.music.db.daos` | `SpeedDialDao`, `SongPlayStatsDao`, `RecommendationExclusionDao`, `TasteProfileDao` (§9). |
| `com.revenge.music.db.entities` | The entity classes and relation projections of §4, the `@DatabaseView` types of §5, and the metadata extension properties of §4.9. |

Additional required artifacts (not implementation shape):

- Exported Room schemas `1.json` … `48.json` under
  `core/schemas/com.revenge.music.db.InternalDatabase/`, checked in.
- A converter round-trip test covering the cases in §11.2.

`core/build.gradle.kts` is in scope as described in §1.

---

## 3. `com.revenge.music.db.Converters`

`class Converters` — Room `@TypeConverter` pair, applied at database level via
`@TypeConverters(Converters::class)` on `InternalDatabase`.

| Function | Signature | Contract |
|---|---|---|
| `fromTimestamp` | `fun fromTimestamp(value: Long?): LocalDateTime?` | `null` → `null`. Otherwise interpret the `Long` as epoch **milliseconds** and return the `LocalDateTime` at `ZoneOffset.UTC` (no system timezone conversion). |
| `dateToTimestamp` | `fun dateToTimestamp(date: LocalDateTime?): Long?` | `null` → `null`. Otherwise interpret the `LocalDateTime` as UTC wall-clock and return epoch millis. |

Round-trip invariant: `fromTimestamp(dateToTimestamp(x)) == x` for every `x` representable in
millis, including leap days and 23:59:59.999. The same UTC interpretation applies to timestamp
conversions performed by migrations, including 1→2.

Converters are `public` (Room instantiates them), have a no-arg constructor, and hold no state.

---

## 4. Entities and relation projections — package `com.revenge.music.db.entities`

All entity classes are Kotlin `data class`es (structural `equals`/`hashCode`/`copy`) unless noted.
Construction downstream uses named arguments, so parameter names, types, order and defaults below
are compatibility contracts. Room column names are listed explicitly; when a property name equals
the column name it is omitted from the "columns" prose.

### 4.1 `LocalItem`

`sealed class LocalItem` with abstract `val id: String`, `val title: String`,
`val thumbnailUrl: String?`. Implemented by `Song`, `Artist`, `Album`, `Playlist`. It exists so
library UI can render heterogeneous items; it has no Room annotations.

### 4.2 Song

`@Immutable @Entity(tableName = "song", indices = [Index("albumId")])`

`data class SongEntity` primary key `id: String`, parameters in this order:

`id: String`, `title: String`, `duration: Int = -1`, `thumbnailUrl: String? = null`,
`albumId: String? = null`, `albumName: String? = null`, `explicit: Boolean = false`
(`@ColumnInfo(defaultValue = "0")`), `year: Int? = null`, `date: LocalDateTime? = null`,
`dateModified: LocalDateTime? = null`, `liked: Boolean = false`, `likedDate: LocalDateTime? = null`,
`totalPlayTime: Long = 0`, `inLibrary: LocalDateTime? = null`, `dateDownload: LocalDateTime? = null`,
`isLocal: Boolean = false` (column `isLocal`, default `false`),
`libraryAddToken: String? = null`, `libraryRemoveToken: String? = null`,
`lyricsOffset: Int = 0` (default `0`), `romanizeLyrics: Boolean = true` (default `true`),
`isDownloaded: Boolean = false` (default `0`), `isUploaded: Boolean = false` (column `isUploaded`,
default `false`), `isVideo: Boolean = false` (column `isVideo`, default `false`),
`hideFromQuickPicks: Boolean = false` (default `0`).

Behaviour:
- `localToggleLike()` returns a copy with `liked` flipped; `likedDate` becomes `now` when the new
  state is liked and `null` when unliked.
- `toggleLike()` returns a copy that flips `liked` and `likedDate` as above, and additionally sets
  `inLibrary` to `now` when transitioning to liked and `inLibrary` was null; when unliking it keeps
  `inLibrary` unchanged. It also performs a best-effort YouTube like/unlike
  (`YouTube.likeVideo(id, !liked)`) that runs off the calling thread; the returned value must not
  depend on the network result.
- `toggleLibrary(syncToYouTube: Boolean = true)` flips `inLibrary` between `now` and `null`; when the
  new state is "not in library" it also clears `liked` and `likedDate`. Side effect (only when
  `syncToYouTube`): best-effort `YouTube.toggleSongLibrary(id, inLibrary != null)` off the calling
  thread.
- `toggleUploaded()` flips `isUploaded`; `toggleHideFromQuickPicks()` flips `hideFromQuickPicks`.
- No method may throw when offline; network calls are best-effort.

`@Immutable data class Song` (a Room relation projection, not a table) —
`@JvmOverloads constructor`:

- `@Embedded val song: SongEntity`
- `@Relation(entity = ArtistEntity::class, entityColumn = "id", parentColumn = "id",
  associateBy = Junction(SortedSongArtistMap::class, parentColumn = "songId", entityColumn = "artistId"))`
  `val artists: List<ArtistEntity>` — ordered by `position` through the view.
- `@Relation(entity = AlbumEntity::class, entityColumn = "id", parentColumn = "id",
  associateBy = Junction(SongAlbumMap::class, parentColumn = "songId", entityColumn = "albumId"))`
  `val album: AlbumEntity? = null`
- `@Relation(parentColumn = "id", entityColumn = "id") val format: FormatEntity? = null`

`Song : LocalItem()`. Overridden `id`, `title` delegate to the embedded row. `thumbnailUrl`:
if `song.isLocal` is false, return `song.thumbnailUrl`; if local, parse `song.albumId` removing a
`"LOCAL_ALBUM_"` prefix, and when it parses to a positive `Long` return
`"content://media/external/audio/albumart/<id>"`; otherwise return `song.thumbnailUrl ?: song.id`.
`romanizeLyrics` delegates to `song.romanizeLyrics`.

### 4.3 Artist

`@Immutable @Entity(tableName = "artist")`

`data class ArtistEntity(@PrimaryKey val id: String, val name: String,
val thumbnailUrl: String? = null, val channelId: String? = null,
val lastUpdateTime: LocalDateTime = LocalDateTime.now(), val bookmarkedAt: LocalDateTime? = null,
@ColumnInfo(name = "isLocal", defaultValue = "false") val isLocal: Boolean = false)`

Behaviour:
- `isYouTubeArtist`: true when `id` starts with `"UC"` or with the private-artist prefix.
- `isPrivatelyOwnedArtist`: true when `id` starts with
  `"FEmusic_library_privately_owned_artist"` (private constant; not part of the public API).
- `localToggleLike()`: copy with `bookmarkedAt = now` if null else `null`.
- `toggleLike()`: `localToggleLike()` plus best-effort
  `YouTube.subscribeChannel(channelId ?: YouTube.getChannelId(id), subscribed)` off the calling
  thread, skipped when the resolved channel id is empty.
- Companion: `fun generateArtistId(): String` → `"LA"` + 8 random alphanumeric characters
  (letters/digits), used for locally created artists.

`@Immutable data class Artist(@Embedded val artist: ArtistEntity, val songCount: Int,
val timeListened: Int? = 0) : LocalItem()` — `id`, `title` (= `artist.name`), `thumbnailUrl` delegate.

### 4.4 Album

`@Immutable @Entity(tableName = "album")`

`data class AlbumEntity(@PrimaryKey val id: String, val playlistId: String? = null,
val title: String, val year: Int? = null, val thumbnailUrl: String? = null,
val themeColor: Int? = null, val songCount: Int, val duration: Int,
@ColumnInfo(defaultValue = "0") val explicit: Boolean = false,
val lastUpdateTime: LocalDateTime = LocalDateTime.now(), val bookmarkedAt: LocalDateTime? = null,
val likedDate: LocalDateTime? = null, val inLibrary: LocalDateTime? = null,
val description: String? = null, @ColumnInfo(name = "isLocal", defaultValue = "false") val isLocal: Boolean = false,
@ColumnInfo(name = "isUploaded", defaultValue = "false") val isUploaded: Boolean = false)`

Behaviour: `localToggleLike()` flips `bookmarkedAt` between `now` and null; `toggleLike()` does the
same and performs `YouTube.likePlaylist(playlistId, becameLiked)` best-effort off the calling thread
when `playlistId` is non-null; `toggleUploaded()` flips `isUploaded`; `toggleLibrary()` flips
`inLibrary` between `now` and null.

`@Immutable data class Album(@Embedded val album: AlbumEntity,
@Relation(entity = ArtistEntity::class, entityColumn = "id", parentColumn = "id",
associateBy = Junction(AlbumArtistMap::class, parentColumn = "albumId", entityColumn = "artistId"))
val artists: List<ArtistEntity> = emptyList(), val songCountListened: Int? = 0,
val timeListened: Long? = 0) : LocalItem()` with `id/title/thumbnailUrl` delegated and
`val description: String? get() = album.description`.

`@Immutable data class AlbumWithSongs(@Embedded val album: AlbumEntity, <same artist relation as Album> val artists: List<ArtistEntity>,
@Relation(entity = SongEntity::class, entityColumn = "id", parentColumn = "id",
associateBy = Junction(SortedSongAlbumMap::class, parentColumn = "albumId", entityColumn = "songId"))
val songs: List<Song>)`.

### 4.5 Playlist

`@Immutable @Entity(tableName = "playlist")`

`data class PlaylistEntity(@PrimaryKey val id: String = generatePlaylistId(), val name: String,
val browseId: String? = null, val createdAt: LocalDateTime? = LocalDateTime.now(),
val lastUpdateTime: LocalDateTime? = LocalDateTime.now(),
@ColumnInfo(name = "isEditable", defaultValue = "true") val isEditable: Boolean = true,
val bookmarkedAt: LocalDateTime? = null, val remoteSongCount: Int? = null,
val playEndpointParams: String? = null, val thumbnailUrl: String? = null,
val shuffleEndpointParams: String? = null, val radioEndpointParams: String? = null,
@ColumnInfo(name = "isLocal", defaultValue = "false") val isLocal: Boolean = false,
@ColumnInfo(name = "isAutoSync", defaultValue = "false") val isAutoSync: Boolean = false,
@ColumnInfo(name = "isPinned", defaultValue = "false") val isPinned: Boolean = false)`

Behaviour:
- `shareLink`: `"https://music.youtube.com/playlist?list=$browseId"` or null when `browseId` null.
- `localToggleLike()`: flip `bookmarkedAt` now/null; `toggleLike()` additionally calls
  `YouTube.likePlaylist(browseId, liked)` best-effort off the calling thread when `browseId` non-null.
- Companion constants: `LIKED_PLAYLIST_ID = "LP_LIKED"`, `DOWNLOADED_PLAYLIST_ID = "LP_DOWNLOADED"`,
  and `fun generatePlaylistId(): String` → `"LP"` + 8 random alphanumerics.

`@Immutable data class Playlist(@Embedded val playlist: PlaylistEntity, val songCount: Int,
@Relation(entity = SongEntity::class, entityColumn = "id", parentColumn = "id",
projection = ["thumbnailUrl"],
associateBy = Junction(PlaylistSongMapPreview::class, parentColumn = "playlistId", entityColumn = "songId"))
val songThumbnails: List<String?>) : LocalItem()`

- `id/title` delegate; `thumbnailUrl` is always `null` (this is intentional: covers use `thumbnails`).
- `thumbnails`: if `playlist.thumbnailUrl` non-null return `listOf(it)`, else
  `songThumbnails.filterNotNull()`.
- `songCount` is supplied by the DAO as a correlated count of `playlist_song_map` rows.

`data class PlaylistSong(@Embedded val map: PlaylistSongMap,
@Relation(parentColumn = "songId", entityColumn = "id", entity = SongEntity::class) val song: Song)`.

### 4.6 Junction tables

| Class | Table | Columns (order) | PK | Indices | FKs |
|---|---|---|---|---|---|
| `SongArtistMap` | `song_artist_map` | `songId TEXT NOT NULL`, `artistId TEXT NOT NULL`, `position INTEGER NOT NULL` | (`songId`,`artistId`) | `index_song_artist_map_songId`, `index_song_artist_map_artistId` | `songId`→`song.id` CASCADE; `artistId`→`artist.id` CASCADE |
| `SongAlbumMap` | `song_album_map` | `songId`, `albumId`, `index INTEGER NOT NULL` | (`songId`,`albumId`) | `index_song_album_map_songId`, `index_song_album_map_albumId` | `songId`→`song.id` CASCADE; `albumId`→`album.id` CASCADE |
| `AlbumArtistMap` | `album_artist_map` | `albumId`, `artistId`, `order INTEGER NOT NULL` | (`albumId`,`artistId`) | `index_album_artist_map_albumId`, `index_album_artist_map_artistId` | `albumId`→`album.id` CASCADE; `artistId`→`artist.id` CASCADE |
| `PlaylistSongMap` | `playlist_song_map` | `id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL`, `playlistId`, `songId`, `position INTEGER NOT NULL`, `setVideoId TEXT` | `id` | `index_playlist_song_map_playlistId`, `index_playlist_song_map_songId` | `playlistId`→`playlist.id` CASCADE; `songId`→`song.id` CASCADE |

All FKs are `ON UPDATE NO ACTION ON DELETE CASCADE`. Constructors:
`SongArtistMap(songId, artistId, position)`, `SongAlbumMap(songId, albumId, index)`,
`AlbumArtistMap(albumId, artistId, order)`,
`PlaylistSongMap(id: Int = 0, playlistId, songId, position: Int = 0, setVideoId: String? = null)`.
All `@ColumnInfo(index = true)` on the FK columns as listed.

### 4.7 Auxiliary entities

| Class | Table | Columns | PK / Index | FKs |
|---|---|---|---|---|
| `FormatEntity` | `format` | `id TEXT NOT NULL`, `itag INTEGER NOT NULL`, `mimeType TEXT NOT NULL`, `codecs TEXT NOT NULL`, `bitrate INTEGER NOT NULL`, `sampleRate INTEGER`, `contentLength INTEGER NOT NULL`, `loudnessDb REAL`, `perceptualLoudnessDb REAL`, `playbackUrl TEXT` | `id` | none |
| `LyricsEntity` | `lyrics` | `id TEXT NOT NULL`, `lyrics TEXT NOT NULL`, `provider TEXT NOT NULL DEFAULT 'Unknown'`, `translatedLyrics TEXT NOT NULL DEFAULT ''`, `translationLanguage TEXT NOT NULL DEFAULT ''`, `translationMode TEXT NOT NULL DEFAULT ''` | `id` | none |
| `Event` | `event` | `id INTEGER PRIMARY KEY AUTOINCREMENT`, `songId TEXT NOT NULL`, `timestamp INTEGER NOT NULL`, `playTime INTEGER NOT NULL` | `id`; index `index_event_songId` | `songId`→`song.id` CASCADE |
| `RelatedSongMap` | `related_song_map` | `id INTEGER PK AUTOINCREMENT`, `songId`, `relatedSongId` | `id`; indices on `songId`, `relatedSongId` | both →`song.id` CASCADE |
| `SetVideoIdEntity` | `set_video_id` | `videoId TEXT NOT NULL`, `setVideoId TEXT` | `videoId` | none |
| `PlayCountEntity` | `playCount` | `song TEXT NOT NULL`, `year INTEGER NOT NULL`, `month INTEGER NOT NULL`, `count INTEGER NOT NULL` | (`song`,`year`,`month`) | none |
| `SearchHistory` | `search_history` | `id INTEGER PK AUTOINCREMENT`, `query TEXT NOT NULL` | `id`; UNIQUE index `index_search_history_query` on `query` | none |
| `RecognitionHistory` | `recognition_history` | `id INTEGER PK AUTOINCREMENT`, `trackId TEXT NOT NULL`, `title`, `artist`, `album?`, `coverArtUrl?`, `coverArtHqUrl?`, `genre?`, `releaseDate?`, `label?`, `shazamUrl?`, `appleMusicUrl?`, `spotifyUrl?`, `isrc?`, `youtubeVideoId?`, `recognizedAt INTEGER NOT NULL`, `liked INTEGER NOT NULL` | `id`; non-unique index `index_recognition_history_trackId` | none |

Constructor notes/defaults:
- `FormatEntity(id, itag, mimeType, codecs, bitrate, sampleRate: Int?, contentLength, loudnessDb: Double?, perceptualLoudnessDb: Double? = null, playbackUrl: String?)`; `playbackUrl` is `@Deprecated` ("retrieve from a fresh player request") but must stay in the schema.
- `LyricsEntity(id, lyrics, provider = "Unknown", translatedLyrics = "", translationLanguage = "", translationMode = "")`; companion `const val LYRICS_NOT_FOUND = "LYRICS_NOT_FOUND"`.
- `Event(id: Long = 0, songId, timestamp: LocalDateTime, playTime: Long)`.
- `RelatedSongMap(id: Long = 0, songId, relatedSongId)`.
- `SetVideoIdEntity(videoId: String = "", setVideoId: String? = null)`.
- `PlayCountEntity(song: String, year: Int = -1, month: Int = -1, count: Int = -1)` — this is a `class`, not a data class; the DAO relies on `@Insert(REPLACE)` semantics keyed by the composite PK.
- `SearchHistory(id: Long = 0, query: String)`.
- `RecognitionHistory(id = 0, trackId, title, artist, album? … youtubeVideoId?, recognizedAt = now, liked = false)`.

`@Immutable data class EventWithSong(@Embedded val event: Event,
@Relation(entity = SongEntity::class, parentColumn = "songId", entityColumn = "id") val song: Song)`.

### 4.8 Newer side tables and stat projections

| Class | Table | Columns | PK |
|---|---|---|---|
| `SpeedDialItem` | `speed_dial_item` | `id TEXT NOT NULL`, `secondaryId TEXT`, `title TEXT NOT NULL`, `subtitle TEXT`, `thumbnailUrl TEXT`, `type TEXT NOT NULL`, `explicit INTEGER NOT NULL`, `createDate INTEGER NOT NULL` | `id` |
| `BeatInfoEntity` | `beat_info` | `songId TEXT NOT NULL`, `bpm REAL NOT NULL`, `firstBeatOffsetMs INTEGER NOT NULL`, `confidence REAL NOT NULL`, `analyzedAt INTEGER NOT NULL`, `mixInPointMs INTEGER`, `mixOutPointMs INTEGER`, `keyPitchClass INTEGER`, `keyIsMinor INTEGER` | `songId` |
| `SongPlayStatsEntity` | `song_play_stats` | `trackKey TEXT NOT NULL`, `title TEXT NOT NULL`, `artist TEXT NOT NULL`, `videoId TEXT`, `artworkUrl TEXT`, `totalPlayTimeMs INTEGER NOT NULL`, `playCount INTEGER NOT NULL`, `skipCount INTEGER NOT NULL`, `lastPlayedAtMillis INTEGER NOT NULL` | `trackKey` |
| `RecommendationExclusionEntity` | `recommendation_exclusions` | `trackKey TEXT NOT NULL`, `excludedAtMillis INTEGER NOT NULL`, `trackName TEXT NOT NULL`, `artistName TEXT NOT NULL` | `trackKey` |
| `TasteProfileEntity` | `taste_profile` | `id INTEGER NOT NULL`, `topArtistsJson TEXT NOT NULL`, `topTracksJson TEXT NOT NULL`, `topGenresJson TEXT NOT NULL`, `confidence REAL NOT NULL`, `updatedAtMillis INTEGER NOT NULL` | `id` |

`BeatInfoEntity(songId, bpm: Float, firstBeatOffsetMs: Long, confidence: Float,
analyzedAt: Long = System.currentTimeMillis(), mixInPointMs: Long? = null, mixOutPointMs: Long? = null,
keyPitchClass: Int? = null, keyIsMinor: Boolean? = null)`; `keyPitchClass` 0=C … 11=B.

`SpeedDialItem(id, secondaryId: String? = null, title, subtitle: String? = null,
thumbnailUrl: String? = null, type: String, explicit: Boolean = false,
createDate: Long = System.currentTimeMillis())`
- `type` is one of the string constants `"SONG" | "ALBUM" | "ARTIST" | "PLAYLIST"` (private).
- `toYTItem(): YTItem` maps back to innertube models; unknown `type` throws `IllegalArgumentException`.
  For `SONG`, `subtitle` is split on `", "` into `Artist(name, id = null)` and `thumbnail = thumbnailUrl.orEmpty()`.
  For `ALBUM`, `id` is `browseId`, `secondaryId` is `playlistId` (or `""`), `artists` split from
  `subtitle` (may be null). For `ARTIST`, only id/title/thumbnail. For `PLAYLIST`, `author` from
  `subtitle`. Endpoints are always null.
- Companion `fromYTItem(item: YTItem): SpeedDialItem` inverse mapping for the four concrete types.

`SongPlayStatsEntity(trackKey, title = "", artist = "", videoId: String? = null,
artworkUrl: String? = null, totalPlayTimeMs: Long = 0L, playCount: Int = 0, skipCount: Int = 0,
lastPlayedAtMillis: Long = 0L)`.

`RecommendationExclusionEntity(trackKey, excludedAtMillis: Long, trackName = "", artistName = "")`.
`TasteProfileEntity(id: Int = 1, topArtistsJson = "", topTracksJson = "", topGenresJson = "",
confidence: Float = 0f, updatedAtMillis: Long = 0L)`.

`@Immutable data class SongWithStats(val id: String, val title: String, val artistName: String? = null,
val thumbnailUrl: String? = null, val songCountListened: Int? = 0, val timeListened: Long? = 0L,
val isVideo: Boolean = false)` — projection only, not a table.

**Track-key invariant (shared with `:app`)**: the canonical `trackKey` for `song_play_stats` and
`recommendation_exclusions` is `"<title.trim()>|<artist.trim()>".lowercase()`. Migration 46→47 must
seed keys that match this formula (`lower(trim(title) || '|' || trim(ifnull(artist,'')))`).

### 4.9 `MetadataExtensions.kt`

Extension properties depends on `:metadata`:
- `val SongEntity.cleanedMetadata: CleanedMetadata` → `MetadataCleaner.clean(title, "")`.
- `val Song.cleanedMetadata: CleanedMetadata` → `MetadataCleaner.clean(title, artists.firstOrNull()?.name.orEmpty())`.
- `val Song.displayTitle: String` → `cleanedMetadata.toDisplayTitle()`.
- `val Song.cleanArtistName: String` → `cleanedMetadata.primaryArtist`.

`CleanedMetadata` must expose `toDisplayTitle(): String` and `primaryArtist: String` (from
`:metadata`). Do not reimplement cleaning here.

---

## 5. Database views — package `com.revenge.music.db.entities`

Three `@DatabaseView` types, declared in `views = [...]` of `@Database`. Their view names and SQL
must match exactly (used by DAO queries and by Room relation junctions):

| Type | View name | Definition |
|---|---|---|
| `SortedSongArtistMap` | `sorted_song_artist_map` | `SELECT * FROM song_artist_map ORDER BY position` |
| `SortedSongAlbumMap` | `sorted_song_album_map` | `SELECT * FROM song_album_map ORDER BY \`index\`` |
| `PlaylistSongMapPreview` | `playlist_song_map_preview` | `SELECT * FROM playlist_song_map WHERE position <= 3 ORDER BY position` |

`SortedSongArtistMap(songId, artistId, position)`; `SortedSongAlbumMap(songId, albumId, index)`;
`PlaylistSongMapPreview(@ColumnInfo(index = true) playlistId, @ColumnInfo(index = true) songId,
idInPlaylist: Int = 0)`. View columns must line up with these names/order.

---

## 6. `InternalDatabase` and `MusicDatabase`

### 6.1 `InternalDatabase`

`@Database(entities = [SongEntity, ArtistEntity, AlbumEntity, PlaylistEntity, SongArtistMap,
SongAlbumMap, AlbumArtistMap, PlaylistSongMap, SearchHistory, FormatEntity, LyricsEntity, Event,
RelatedSongMap, SetVideoIdEntity, PlayCountEntity, RecognitionHistory, SpeedDialItem, BeatInfoEntity,
SongPlayStatsEntity, RecommendationExclusionEntity, TasteProfileEntity], views = [SortedSongArtistMap,
SortedSongAlbumMap, PlaylistSongMapPreview], version = 48, exportSchema = true,
autoMigrations = [...])`
`@TypeConverters(Converters::class)`
`abstract class InternalDatabase : RoomDatabase()`

Abstract members (non-null): `val dao: DatabaseDao`, `val speedDialDao: SpeedDialDao`,
`val songPlayStatsDao: SongPlayStatsDao`, `val recommendationExclusionDao: RecommendationExclusionDao`,
`val tasteProfileDao: TasteProfileDao`.

Companion:
- `const val DB_NAME = "song.db"` — the on-disk filename; backup/restore in `:app` writes and
  restores a zip entry with exactly this name, so it must not change.
- `fun newInstance(context: Context): MusicDatabase` — returns a database opened on `DB_NAME` with:
  - the explicit migration list in §7.4 plus the auto migrations declared on the class;
  - WAL journal mode;
  - independent query and transaction executors (queries must not deadlock against
    `withTransaction`);
  - an on-open configuration that best-effort applies PRAGMA `busy_timeout = 60000`,
    `cache_size = -16000`, `wal_autocheckpoint = 1000`, `synchronous = NORMAL`; each failure only
    logs, never crashes;
  - the opened database wrapped in `MusicDatabase`.
- The `:app` DI module independently builds the same database and additionally runs, on open,
  `DELETE FROM format WHERE (codecs = 'alac' OR bitrate = 1411200) AND id NOT LIKE 'local_%'`.
  Preserve the semantics that this cleanup runs on open (it can live in the shared builder or in
  `:app`; core must not assume `:app` does it).

### 6.2 `MusicDatabase` facade

`class MusicDatabase(private val delegate: InternalDatabase) : DatabaseDao by delegate.dao`
— a thin facade; all `DatabaseDao` methods are exposed by delegation.

Properties (all delegate): `speedDialDao: SpeedDialDao`, `songPlayStatsDao: SongPlayStatsDao`,
`recommendationExclusionDao: RecommendationExclusionDao`, `tasteProfileDao: TasteProfileDao`,
`openHelper: SupportSQLiteOpenHelper`.

Functions:
- `fun query(block: MusicDatabase.() -> Unit)` — runs `block` off the calling thread with
  `this@MusicDatabase` as receiver. Non-blocking, returns immediately. Exceptions in `block` are
  **not** propagated to the caller (fire-and-forget); callers that need results call suspend/blocking
  DAO methods instead.
- `fun transaction(block: MusicDatabase.() -> Unit)` — runs `block` off the calling thread inside a
  database transaction; exceptions roll the transaction back and are not propagated to the caller.
- `suspend fun withTransaction(block: suspend MusicDatabase.() -> Unit)` — suspending transactional
  wrapper; exceptions propagate and roll back.
- `fun close()` — closes the delegate.

Threading contract: all DAO `Flow`/`suspend` methods execute on Room's query executor; `*Blocking`
DAO methods must only be called from within a `query`/`transaction`/`withTransaction` block or a
background thread, never from the main thread.

---

## 7. Migrations and schema history (v1 → v48)

### 7.1 Auto migrations declared in `@Database`

`AutoMigration(2,3)`, `(3,4)`, `(4,5)`, `(5,6, Migration5To6)`, `(6,7, Migration6To7)`,
`(7,8, Migration7To8)`, `(8,9)`, `(9,10, Migration9To10)`, `(10,11, Migration10To11)`,
`(11,12, Migration11To12)`, `(12,13, Migration12To13)`, `(13,14, Migration13To14)`, `(14,15)`,
`(15,16)`, `(16,17, Migration16To17)`, `(17,18)`, `(18,19, Migration18To19)`,
`(19,20, Migration19To20)`, `(20,21, Migration20To21)`, `(21,22, Migration21To22)`,
`(22,23, Migration22To23)`, `(23,24, Migration23To24)`, `(24,25)`, `(25,26)`, `(26,27)`, `(27,28)`,
`(30,31)`, `(32,33)`, `(33,34)`, `(34,35)`, `(35,36)`, `(36,37, Migration36To37Spec)`,
`(41,42, Migration41To42)`, `(46,47, Migration46To47Spec)`, `(47,48, Migration47To48Spec)`.

The path between 28→29, 29→30, 31→32 and 37→41 is covered by explicit `Migration` objects (§7.3).
Room must resolve every consecutive step for a database at any version 1…47.

### 7.2 `AutoMigrationSpec` behaviour

Each row states the observable database state that must hold after the migration.

| Spec | Required post-migration behaviour |
|---|---|
| `Migration5To6` | Every playlist row whose `id` does not start with `LP` has `browseId = id`. |
| `Migration6To7` | Every song has `inLibrary = createDate` (epoch millis value). |
| `Migration7To8` | Columns `song.createDate`, `song.modifyDate` are absent. |
| `Migration9To10` | Table `download` is absent. |
| `Migration10To11` | Columns `song.downloadState`, `artist.bannerUrl`, `artist.description`, `artist.createDate` are absent. |
| `Migration11To12` | Column `album.createDate` is absent; `album.bookmarkedAt = lastUpdateTime` for all albums; any album referenced by `song.albumId`/`albumName` but missing from `album` exists with `songCount=0`, `duration=0`, `lastUpdateTime=0` (no duplicates when it already exists); index `index_song_albumId` exists. |
| `Migration12To13` | No post step. |
| `Migration13To14` | Every playlist's `createdAt` and `lastUpdateTime` equal the migration time. |
| `Migration16To17` | Columns `song.isLocal`, `song.localPath`, `artist.isLocal`, `playlist.isLocal` are absent; `playlist.bookmarkedAt = lastUpdateTime`; `playlist.isEditable = 1` where `browseId IS NOT NULL`. |
| `Migration18To19`, `Migration19To20` | `song.explicit` is 0 wherever it was NULL. |
| `Migration20To21` | Column `song.artistName` is absent. |
| `Migration21To22` | `song.libraryAddToken TEXT DEFAULT ''`, `song.libraryRemoveToken TEXT DEFAULT ''`, `song.romanizeLyrics INTEGER NOT NULL DEFAULT 1`, `song.isDownloaded INTEGER NOT NULL DEFAULT 0` are present. |
| `Migration22To23` | No post step. |
| `Migration23To24` | `song.isUploaded INTEGER NOT NULL DEFAULT 0` is present. |
| `Migration36To37Spec` | No post step. |
| `Migration41To42` | Tables `brain_activity_log`, `play_event`, `taste_profile` are absent. |
| `Migration46To47Spec` | `song_play_stats` is seeded from `event` joined to `song` and aggregated artist names from `song_artist_map`/`artist` (see §4.8 key formula): `trackKey` = lowercase `title|artist`, `title`, `artist` (`''` when absent), `videoId = song.id`, `artworkUrl = song.thumbnailUrl`, `totalPlayTimeMs = sum(event.playTime)`, `playCount = count(*)`, `skipCount = 0`, `lastPlayedAtMillis = max(event.timestamp)`, only for `event.playTime > 0`, grouped by key. The table is empty at this point, so rows cannot conflict. |
| `Migration47To48Spec` | If the legacy `taste_profile` is not already in the §4.8 shape (it has a `genres` column or lacks `topArtistsJson`), it is replaced by the §4.8 shape. A table already in the §4.8 shape is left unchanged (same columns, same data). |

### 7.3 Explicit `Migration` objects (public top-level `val`s)

All are `public val MIGRATION_x_y = object : Migration(x, y) { … }` in `MusicDatabase.kt`.
Names and IDs are part of the API (`:app` references them by name). Each entry states the observable
post-migration state; how it is reached is implementation-defined.

- `MIGRATION_1_2 (1→2)`: upgrade the legacy schema to the v2 baseline. Observable requirements:
  - every legacy artist and playlist integer id is replaced by a stable string id in the
    `generateArtistId()` / `generatePlaylistId()` form (`"LA"`/`"LP"` + 8 alphanumerics); the same
    legacy row maps to the same new id in every table that referenced it;
  - each legacy song ends with exactly one `song_artist_map` entry (`position = 0`) pointing at its
    remapped artist;
  - playlist song positions are gap-free and 0-based per playlist, ordered by the legacy position
    (ties keep legacy insertion order);
  - legacy rows survive without loss; a row that cannot be reinserted aborts the migration rather
    than being silently dropped;
  - timestamps use the UTC epoch-millis interpretation of §3; each artist's `createDate` and
    `lastUpdateTime` both carry its legacy `lastUpdateTime`; songs get `isTrash = 0`;
  - the resulting v2 schema consists of:
    `song(id,title,duration,thumbnailUrl,albumId,albumName,liked,totalPlayTime,isTrash,download_state,create_date,modify_date)`,
    `artist(id,name,thumbnailUrl,bannerUrl,description,createDate,lastUpdateTime)`,
    `album(id,title,year,thumbnailUrl,songCount,duration,createDate,lastUpdateTime)`,
    `playlist(id,name,author,authorId,year,thumbnailUrl,createDate,lastUpdateTime)`,
    the junction tables, `download`, `search_history` (with its unique query index), and views
    `sorted_song_artist_map` and `playlist_song_map_preview`.
- `MIGRATION_21_24 (21→24)`: after migration, `song.libraryAddToken TEXT DEFAULT ''`,
  `.libraryRemoveToken TEXT DEFAULT ''`, `.romanizeLyrics INTEGER NOT NULL DEFAULT 1`,
  `.isDownloaded INTEGER NOT NULL DEFAULT 0`, `.isUploaded INTEGER NOT NULL DEFAULT 0` are present.
- `MIGRATION_22_24 (22→24)`: after migration, `song.isUploaded INTEGER NOT NULL DEFAULT 0` is present.
- `MIGRATION_24_25 (24→25)`: after migration, `format.perceptualLoudnessDb REAL DEFAULT NULL` is present.
- `MIGRATION_27_28 (27→28)`: after migration, `song.lyricsOffset INTEGER NOT NULL DEFAULT 0` and
  `song.isVideo INTEGER NOT NULL DEFAULT 0` are present, and `song`, `artist`, `album`, `playlist`
  have exactly the §4 column shapes. `lyricsOffset`/`isVideo` carry the old value when the source
  had an equivalent column and 0 otherwise. `playlist.isAutoSync = 0`, index `index_song_albumId`
  exists, and the three §5 views exist with their definitions.
- `MIGRATION_28_29 (28→29)`: after migration, `playlist.isAutoSync INTEGER NOT NULL DEFAULT false`
  is present and the three §5 views exist.
- `MIGRATION_29_30 (29→30)`: after migration, `song.isVideo INTEGER NOT NULL DEFAULT 0` and
  `lyrics.provider TEXT NOT NULL DEFAULT 'Unknown'` are present and the three §5 views exist.
- `MIGRATION_31_32 (31→32)`: after migration, `lyrics.provider TEXT NOT NULL DEFAULT 'Unknown'`,
  `lyrics.translatedLyrics TEXT NOT NULL DEFAULT ''`, `lyrics.translationLanguage TEXT NOT NULL DEFAULT ''`,
  `lyrics.translationMode TEXT NOT NULL DEFAULT ''` are present.
- `MIGRATION_36_37 (36→37)`: intentionally empty (guards downgrades).
- `MIGRATION_37_38 (37→38)`: after migration the tables
  `brain_activity_log(id PK AUTOINCREMENT, action TEXT NOT NULL, reason TEXT NOT NULL, timestamp INTEGER NOT NULL)`,
  `play_event(id PK AUTOINCREMENT, trackId TEXT NOT NULL, startTime INTEGER NOT NULL, durationMs INTEGER NOT NULL, skipped INTEGER NOT NULL, engaged INTEGER NOT NULL)`,
  and `taste_profile(id INTEGER NOT NULL, genres TEXT NOT NULL, confidence REAL NOT NULL, patternsFound INTEGER NOT NULL, modelVersion TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))`
  exist, and `format.perceptualLoudnessDb REAL DEFAULT NULL` is present.
- `MIGRATION_38_39 (38→39)`: after migration, `format.perceptualLoudnessDb REAL DEFAULT NULL` is present.
- `MIGRATION_39_40 (39→40)`: after migration, `beat_info(songId TEXT PK, bpm REAL NOT NULL,
  firstBeatOffsetMs INTEGER NOT NULL, confidence REAL NOT NULL, analyzedAt INTEGER NOT NULL)` exists.
- `MIGRATION_40_41 (40→41)`: after migration, `beat_info.mixInPointMs INTEGER DEFAULT NULL` and
  `beat_info.mixOutPointMs INTEGER DEFAULT NULL` are present.
- `MIGRATION_41_42 (41→42)`: no DDL itself (the paired §7.2 spec removes the legacy tables).
- `MIGRATION_42_43 (42→43)`: after migration, `playlist` has all its prior columns plus
  `isPinned INTEGER NOT NULL DEFAULT false`, with the primary key and existing row values preserved.
- `MIGRATION_43_44 (43→44)`: after migration, `beat_info.keyPitchClass INTEGER DEFAULT NULL` and
  `beat_info.keyIsMinor INTEGER DEFAULT NULL` are present.
- `MIGRATION_44_45 (44→45)`: after migration, `song`, `artist` and `playlist` each have
  `isLocal INTEGER NOT NULL DEFAULT 0`.
- `MIGRATION_45_46 (45→46)`: after migration, `song.hideFromQuickPicks INTEGER NOT NULL DEFAULT 0`
  is present.

Add-column requirements in this section and §7.2 are idempotent: running the migration on a
database that already has the column must not fail and must not duplicate it. Likewise, a migration
that rebuilds a table must leave the three §5 views present with their definitions on completion.

### 7.4 Migrations registered on the builder

`newInstance` (and `:app`'s duplicate builder) must register, in any order:
`MIGRATION_1_2, MIGRATION_21_24, MIGRATION_22_24, MIGRATION_24_25, MIGRATION_27_28, MIGRATION_28_29,
MIGRATION_29_30, MIGRATION_31_32, MIGRATION_36_37, MIGRATION_37_38, MIGRATION_38_39, MIGRATION_39_40,
MIGRATION_40_41, MIGRATION_41_42, MIGRATION_42_43, MIGRATION_43_44, MIGRATION_44_45, MIGRATION_45_46`.

If `26→27` or any other path lacks an object, Room's auto migration from the `@Database`
declaration fills it. Do not remove an explicit migration that shadows an auto migration (27→28,
36→37, 41→42); the explicit version carries behaviour the auto path does not (e.g. preserving data
through a rebuild).

### 7.5 Current schema snapshot (v48)

Authoritative source is `core/schemas/com.revenge.music.db.InternalDatabase/48.json`. Column
order, defaults, nullability, PKs, indices and FKs are as laid out in §4 and §5. The database must
report `version = 48`; opening a v48 file must require no migration.

---

## 8. `DatabaseDao` — package `com.revenge.music.db`

`@Dao interface DatabaseDao`. All methods listed are part of the public API used by `:app`.
Unless stated, list-returning methods return a `Flow<List<T>>` that emits on any change to the
underlying tables (Room invalidation). `@Transaction` methods load relations in one transaction.
Tie-break ordering is SQLite row order unless specified.

### 8.1 Sort helpers (non-abstract, default implementations)

- `fun songs(sortType: SongSortType, descending: Boolean): Flow<List<Song>>`
- `fun likedSongs(sortType: SongSortType, descending: Boolean): Flow<List<Song>>`
- `fun artistSongs(artistId: String, sortType: ArtistSongSortType, descending: Boolean,
  fromTimeStamp: Long? = null, toTimeStamp: Long? = null, limit: Int = -1): Flow<List<Song>>`
- `fun downloadedSongs(sortType: SongSortType, descending: Boolean): Flow<List<Song>>`
- `fun uploadedSongs(sortType: SongSortType, descending: Boolean): Flow<List<Song>>`
- `fun artists(sortType: ArtistSortType, descending: Boolean): Flow<List<Artist>>`
- `fun artistsBookmarked(sortType: ArtistSortType, descending: Boolean): Flow<List<Artist>>`
- `fun albums(sortType: AlbumSortType, descending: Boolean): Flow<List<Album>>`
- `fun albumsLiked(...)`, `fun albumsUploaded(...)`
- `fun playlists(sortType: PlaylistSortType, descending: Boolean): Flow<List<Playlist>>`

Semantics:
- `SongSortType` values: `CREATE_DATE, NAME, ARTIST, PLAY_TIME`. `CREATE_DATE` is row-id order,
  `NAME` is a locale-aware case/accent-insensitive title order, `ARTIST` sorts by concatenated
  artist names, groups by album title, then orders within a group by artist name, `PLAY_TIME` is by
  `totalPlayTime`. `descending` reverses the final order.
- `ArtistSongSortType`: `CREATE_DATE, NAME, PLAY_TIME`; `PLAY_TIME` uses the
  `mostPlayedSongsByArtist` result when both timestamps are non-null. `limit > 0` takes the first N
  *before* applying `descending`.
- `ArtistSortType`: `CREATE_DATE, NAME, SONG_COUNT, PLAY_TIME`. Both artist list helpers filter the
  result to artists where `isYouTubeArtist || isLocal`.
- `AlbumSortType`: `CREATE_DATE, NAME, ARTIST, YEAR, SONG_COUNT, LENGTH, PLAY_TIME`.
- `PlaylistSortType`: `CREATE_DATE, NAME, SONG_COUNT, LAST_UPDATED`. `playlists` orders descending
  as requested, then partitions pinned playlists first (stable within each partition).

### 8.2 Library song queries

- `songsByRowIdAsc()`, `songsByCreateDateAsc()`, `songsByNameAsc()`, `songsByPlayTimeAsc()`
  — all only in-library songs (`inLibrary IS NOT NULL`), ordered by rowid / `inLibrary` / `title` /
  `totalPlayTime`.
- `topSongs(limit: Int)` — all songs ordered by `totalPlayTime DESC LIMIT :limit` (does not require `inLibrary`).
- `likedSongsByRowIdAsc/CreateDateAsc/ByNameAsc/ByPlayTimeAsc()` — liked songs only.
- `likedSongsCount(): Flow<Int>` — count of liked songs.
- `albumSongs(albumId): Flow<List<Song>>` — songs mapped to the album via `song_album_map`, unordered.
- `playlistSongs(playlistId): Flow<List<PlaylistSong>>` — ordered by `position`; `PlaylistSong.song` loaded.
- `artistSongsByCreateDateAsc/ByNameAsc/ByPlayTimeAsc(artistId)` — songs mapped to the artist via
  `song_artist_map`, in-library only, ordered by `inLibrary`/`title`/`totalPlayTime`.
- `mostPlayedSongsByArtist(artistId, fromTimeStamp, toTimeStamp)` — per song, total `event.playTime`
  in range for that artist, `ORDER BY played DESC`; returns the song projection only.
- `artistSongsPreview(artistId, previewSize: Int = 3)` — mapped in-library songs, `LIMIT previewSize`.

### 8.3 Recommendation queries

- `quickPicks(now: Long = System.currentTimeMillis(), randomSeed: Int = Random.nextInt(1, Int.MAX_VALUE) or 1)`
  — union of: last 5 played songs (by event rowid desc), top 5 by summed playtime in the last 7 days,
  and 10 random library songs ranked by `((rowid * randomSeed) & 2147483647), id`; excludes
  `hideFromQuickPicks = 1`; deduplicates by song; final `ORDER BY` the same expression, `LIMIT 100`.
  The same `randomSeed` must produce the same order for the lifetime of one query/Flow emission.
- `hiddenFromQuickPicksSongIds(): Flow<List<String>>`.
- `recentlyPlayedSongs(limit: Int = 50)` — songs joined with `event`, grouped by song, ordered by
  `MAX(event.timestamp) DESC, MAX(event.rowId) DESC`.
- `getRecommendationAlbum(now, limit = 5, offset = 0)` and `recommendedAlbum(now, limit, offset)` —
  songs whose `albumId` was played in the last 14 days, grouped by album, `HAVING albumId IS NOT NULL`,
  ordered by summed playtime desc, paged. Both exist for compatibility even though their results are
  equivalent.
- `mostPlayedSongsStats(fromTimeStamp, limit = 6, offset = 0, toTimeStamp = now)` — `SongWithStats`
  projection: id, title, thumbnailUrl, isVideo, one artist name, per-range listened count, time.
- `mostPlayedSongs(fromTimeStamp, limit = 6, offset = 0, toTimeStamp = now, hideVideoSongs = false)`
  and `leastPlayedSongs(...)` — exclude hidden songs; when `hideVideoSongs` true exclude `isVideo = 1`.
  Range is `timestamp > from AND timestamp <= to` (inclusive upper bound).
- `mostPlayedArtists(fromTimeStamp, limit = 6, offset = 0, toTimeStamp = now)`.
- `mostPlayedAlbums(fromTimeStamp, limit = 6, offset = 0, toTimeStamp = now)`.
- `forgottenFavorites(now, randomSeed)` — songs with high pre-30-day playtime and low 30-day playtime
  (`0.2 * old > new`), excluding hidden songs, random order via the same seed expression, `LIMIT 100`.
- `artistAlbumsPreview(artistId, previewSize = 6)`.
- `getLifetimePlayCount(songId: String?): Flow<Int>`, `getPlayCountByYear(songId, year)`,
  `getPlayCountByMonth(songId, year, month)` — read `playCount`; null song id yields `null` sum
  (Kotlin `Int?` flow value may be null; callers handle it).

### 8.4 Statistics

- `getTotalPlayTimeInRange(from, to): Flow<Long?>` (`timestamp >= from AND <= to`).
- `getUniqueSongCountInRange`, `getUniqueArtistCountInRange`, `getUniqueAlbumCountInRange`
  — distinct counts over events in the inclusive range; artist count joins `song_artist_map`,
  album count joins `song`.
- `getPlayTimeForDay(from, to): Flow<Long>`, `getSongsPlayTimeForDay`, `getArtistPlayTimeForDay`,
  `getAlbumPlayTimeForDay` — all return the summed `playTime` (0 when none) over
  `timestamp >= from AND < to`. The four methods are intentionally equivalent; each exists so
  callers compile.

### 8.5 Single-item lookups

- `song(songId: String?): Flow<Song?>`
- `suspend getSongById(songId: String): Song?`
- `getSongByIdBlocking(songId: String): Song?` (blocking; call inside query/transaction blocks)
- `suspend getSongsByIds(songIds: List<String>): List<Song>`
- `getSongsByIdsFlow(songIds: List<String>): Flow<List<Song>>`
- `songArtistMap(songId): List<SongArtistMap>` (blocking, non-Flow)
- `allSongs(): Flow<List<Song>>`, `carouselLibrarySongs(): Flow<List<Song>>` — the latter selects
  songs in library OR liked OR referenced by any playlist, newest rowid first.
- `allArtistsByPlayTime(): Flow<List<Artist>>` — distinct artists with songCount, ordered by whether
  they have playtime then playtime desc; artists with no plays sort after.
- `suspend getSetVideoId(videoId): SetVideoIdEntity?`
- `format(id: String?): Flow<FormatEntity?>`, `lyrics(id: String?): Flow<LyricsEntity?>`
- `artist(id): Flow<Artist?>`
- `album(id): Flow<Album?>`, `albumWithSongs(albumId): Flow<AlbumWithSongs?>`,
  `albumArtistMaps(albumId): List<AlbumArtistMap>` (blocking)
- `playlist(playlistId): Flow<Playlist?>`, `suspend getPlaylistById(playlistId): Playlist?`,
  `getPlaylistByIdBlocking(playlistId): Playlist?`, `playlistByBrowseId(browseId): Flow<Playlist?>`
- `beatInfo(songId): BeatInfoEntity?` (blocking)
- `suspend getArtistById(id): ArtistEntity?`, `artistByName(name): ArtistEntity?` (blocking)

The `Playlist` variants all compute `songCount` as a correlated count over `playlist_song_map`.

### 8.6 Artist and album listings

- Artists: `artistsByCreateDateAsc/ByNameAsc/BySongCountAsc/ByPlayTimeAsc()`,
  `artistsBookmarkedBy{CREATE_DATE,NAME,SONG_COUNT,PLAY_TIME}Asc()`. The non-bookmarked variants
  only include artists with `songCount > 0`; `songCount` counts in-library songs only.
- Albums: `albumsBy{CreateDate,Name,Year,SongCount,Length,PlayTime}Asc()` — only albums that have at
  least one in-library song; `albumsLikedBy*` filtered by `bookmarkedAt IS NOT NULL`;
  `albumsUploadedBy*` — note the existing queries for `CreateDate` filter `isUploaded = 1`, the other
  sort columns use `bookmarkedAt IS NOT NULL` (this inconsistency is observable behaviour and must be
  reproduced unless intentionally reconciled with a documented migration; treat existing behaviour as
  the contract).

### 8.7 Playlist membership and ordering

- `checkInPlaylist(playlistId, songId): Int` — `0` or `1`, whether the song is in the playlist; blocking.
- `playlistDuplicates(playlistId, songIds): List<String>` — ids already present.
- `nextPlaylistPosition(playlistId): Int` — `COALESCE(MAX(position), -1) + 1`.
- `addSongToPlaylist(playlist: Playlist, songIds: List<String>)` — transactional; appended songs
  receive consecutive positions starting at `nextPlaylistPosition(playlist.id)` (never
  `playlist.songCount`), in list order.
- `move(playlistId, fromPosition, toPosition)` — renumbers the rows within the closed interval
  `[min(from,to), max(from,to)]` so that the row at `fromPosition` ends at `toPosition` and the rows
  it crosses shift by one towards the vacated slot; a no-op when `from == to`.
- `clearPlaylist(playlistId)` — removes all playlist/song mappings for the playlist.
- `playlistSongMaps(songId): List<PlaylistSongMap>` and
  `playlistSongMaps(playlistId, from: Int): List<PlaylistSongMap>` (position >= from, ordered).
- `suspend getPlaylistById`, `getPlaylistByIdBlocking` as above.

### 8.8 Download / upload state

- `downloadedSongsByCreateDateAsc()` — downloaded songs (`isDownloaded = 1`) ordered by
  `dateDownload ASC, rowId ASC`; `downloadedSongsByNameAsc()` (by title), `downloadedSongsByPlayTimeAsc()`.
- `updateDownloadedInfo(songId: String, downloaded: Boolean, date: LocalDateTime?)` — writes
  `isDownloaded` and `dateDownload`; called on download success (date = completion time) and on
  cancel/failure (`false`, null). Must be non-suspending and cheap.
- `uploadedSongsByCreateDateAsc()` (by `dateDownload`), `ByNameAsc`, `ByPlayTimeAsc`, `ByRowIdAsc`.
- `uploadedSongs(sortType, descending)` mirrors `downloadedSongs`.

### 8.9 Search

- `searchSongs(query, previewSize = Int.MAX_VALUE)` — title `LIKE '%query%'`, in-library only.
- `searchArtists(query, previewSize)` — name match, `songCount > 0`.
- `searchAlbums(query, previewSize)` — title match and at least one in-library song.
- `searchPlaylists(query, previewSize)` — name match, includes `songCount`.
Empty `query` matches everything (`%%`). Matching is SQLite `LIKE`, i.e. case-insensitive for ASCII.

### 8.10 History

- `events(): Flow<List<EventWithSong>>` — all events, newest rowid first.
- `eventsForPeriod(from, to): Flow<List<EventWithSong>>` — `timestamp >= from AND < to`.
- `firstEvent(): Flow<EventWithSong?>` — lowest rowid.
- `eventCount(): Flow<Int>`.
- `clearListenHistory()` — delete all events.
- `searchHistory(query: String = ""): Flow<List<SearchHistory>>` — prefix match
  `query LIKE :query || '%'`, newest id first. An empty string matches all.
- `clearSearchHistory()`.
- Recognition history: `recognitionHistory()` (newest `recognizedAt` first), `recognitionHistoryById(id)`,
  `searchRecognitionHistory(query)` (title OR artist contains), `clearRecognitionHistory()`,
  `deleteRecognitionHistoryById(id)`, `updateRecognitionHistoryLiked(id, liked)`,
  `insert(RecognitionHistory): Long` (REPLACE), `delete(RecognitionHistory)`.

### 8.11 Counters and status

- `incrementTotalPlayTime(songId, playTime: Long)` — adds `playTime` to `totalPlayTime`.
- `updateLikedStatus(songId, liked: Boolean, likedDate: LocalDateTime?)`.
- `incrementPlayCount(songId, year, month)` — increments the matching `playCount` row; if absent,
  nothing changes (the 3-arg overload is raw).
- `incrementPlayCount(songId)` — transactional: for the current UTC year/month, `count` increases by
  1; a pre-existing count is preserved (resulting count is previous + 1), and a missing row is
  created.
- `inLibrary(songId, inLibrary: LocalDateTime?)`.
- `addLibraryTokens(songId, libraryAddToken: String?, libraryRemoveToken: String?)`.

### 8.12 Related songs

- `hasRelatedSongs(songId): Boolean` (blocking).
- `getRelatedSongs(songId): Flow<List<Song>>` and `relatedSongs(songId): List<Song>` (blocking) —
  each related song appears once even if the map holds duplicates.

### 8.13 Insert / update / upsert / delete

Insert conflict strategies are load-bearing:

| Method | Strategy | Notes |
|---|---|---|
| `insert(SongEntity): Long` | IGNORE | `-1` means row already existed. |
| `insert(ArtistEntity)` | IGNORE | |
| `insert(AlbumEntity): Long` | IGNORE | |
| `insert(PlaylistEntity)` | IGNORE | |
| `insert(SongArtistMap)`, `insert(SongAlbumMap)`, `insert(AlbumArtistMap)`, `insert(PlaylistSongMap)`, `insert(RelatedSongMap)` | IGNORE under a public wrapper that swallows exceptions | FK violations / duplicates must not crash callers. |
| `insert(SearchHistory)` | REPLACE | unique `query` — re-searching moves the row (new id). |
| `insert(Event)` | IGNORE | |
| `insert(PlayCountEntity): Long` | IGNORE | |
| `insert(RecognitionHistory): Long` | REPLACE | |
| `upsert(SongEntity)`, `upsert(SongAlbumMap)`, `upsert(LyricsEntity)`, `upsert(FormatEntity)`, `upsert(BeatInfoEntity)` | Room `@Upsert` | |
| `update(SongEntity)`, `update(ArtistEntity)`, `update(AlbumEntity)`, `update(PlaylistEntity)`, `update(PlaylistSongMap)` | `@Update` | no-op when the row is missing. |
| `delete(...)` for `SongEntity`, `SongArtistMap`, `ArtistEntity`, `AlbumEntity`, `AlbumArtistMap`, `PlaylistEntity`, `PlaylistSongMap`, `LyricsEntity`, `SearchHistory`, `Event` | `@Delete` | cascade cleans children. |
| `deleteFormat(id)`, `deletePlaylistById(browseId)` | delete query | |

Composite transactional operations (each must run atomically):

- `insert(mediaMetadata: MediaMetadata, block: (SongEntity) -> SongEntity = { it })`:
  persists `block(mediaMetadata.toSongEntity())`. When the song already exists, nothing else
  changes. Otherwise each artist, in declaration order, resolves to an id (`artist.id`, else an
  existing artist with the same name, else a newly generated id), missing artists are created with
  `channelId` taken from the metadata artist id, and the song is linked to each artist at
  `position = index`. Re-running the same call must not duplicate rows or mappings.
- `insert(albumPage: AlbumPage)`: persists the album with `id = album.browseId`,
  `playlistId`, `title`, `year`, `thumbnailUrl`, `songCount = songs.size`,
  `duration = sum(song.duration ?: 0)`, `explicit = album.explicit || any song explicit`,
  `description = albumPage.description`, and does nothing further when the album already exists.
  Otherwise each song is inserted from its metadata (updating the existing row when present) and
  mapped to the album at its index, and the album's artists are persisted and mapped at their order.
- `update(song: Song, mediaMetadata: MediaMetadata)`: after the call the song row carries
  title/duration/thumbnailUrl/albumId/albumName/libraryAddToken/libraryRemoveToken from the
  metadata, and its artist links reflect the metadata's artists.
- `update(artist: ArtistEntity, artistPage: ArtistPage)`: after the call the artist row has
  `name = artistPage.artist.title`, thumbnail = `artistPage.artist.thumbnail` resized to 544×544,
  and `lastUpdateTime = now`. (`resize` is `com.revenge.music.ui.utils.resize(width: Int?, height: Int?): String` in `:core`.)
- `update(album: AlbumEntity, albumPage: AlbumPage, artists: List<ArtistEntity>? = emptyList())`:
  after the call the album row reflects the remote album (description falling back to the existing
  value), supplied artists that the remote no longer lists are deleted, songs and album mappings are
  upserted, and when the remote lists artists, the album's artist mappings reflect the resolved
  artist list.
- `update(playlistEntity: PlaylistEntity, playlistItem: PlaylistItem)`: after the call the playlist
  row carries name, browseId, thumbnailUrl, isEditable, `remoteSongCount` = first integer found in
  `songCountText` (or null), and `playEndpointParams`/`shuffleEndpointParams`/`radioEndpointParams`
  from the corresponding endpoint `params`.

### 8.14 Maintenance

- `@RawQuery raw(query: SupportSQLiteQuery): Int`.
- `checkpoint()` — `PRAGMA wal_checkpoint(FULL)`. Blocking.
- `localSongs(): Flow<List<Song>>` — local songs ordered by title (case-insensitive) then id.
- `suspend localSongIds(): List<String>`, `clearLocalSongs()`,
  `pruneLocalAlbums()` (delete local albums no longer referenced by any local song),
  `pruneLocalArtists()` (delete local artists no longer referenced by any local song),
  `suspend getArtistEntitiesByIds(ids)`, `suspend getAlbumEntitiesByIds(ids)`,
  `deleteSongAlbumMaps(songId)`, `pruneFormats()` (delete formats whose id is not a song id),
  `prunePlayCounts()` (delete playCounts whose `song` is not a song id),
  `deleteSongsByIds(songIds)`, `deleteSongArtistMaps(songId)`,
  `deleteAlbumArtistMapsByAlbumIds(albumIds)`.

---

## 9. Side DAOs — package `com.revenge.music.db.daos`

### 9.1 `SpeedDialDao`

- `fun getAll(): Flow<List<SpeedDialItem>>` — `ORDER BY createDate ASC`.
- `suspend fun insert(item: SpeedDialItem)` — REPLACE.
- `suspend fun delete(id: String)`.
- `fun isPinned(id: String): Flow<Boolean>` — whether a row with that id exists.

### 9.2 `SongPlayStatsDao`

- `suspend fun find(trackKey: String): SongPlayStatsEntity?`
- `suspend fun findByKeys(trackKeys: List<String>): List<SongPlayStatsEntity>`
- `suspend fun upsert(entity: SongPlayStatsEntity)`
- `suspend fun mostPlayedSince(sinceMillis: Long, limit: Int): List<SongPlayStatsEntity>`
  — `lastPlayedAtMillis >= since`, `ORDER BY totalPlayTimeMs DESC LIMIT limit`.
- `suspend fun recentlyPlayed(limit: Int)` — `ORDER BY lastPlayedAtMillis DESC LIMIT limit`.
- `fun observeTopPlayed(limit: Int): Flow<List<SongPlayStatsEntity>>` — `totalPlayTimeMs DESC`.
- `suspend fun getAllTrackKeys(): List<String>`
- `suspend fun clearAll()`

### 9.3 `RecommendationExclusionDao`

- `suspend fun upsert(entity)`, `suspend fun upsertAll(entities: List<…>)`
- `suspend fun getAll(): List<…>` and `fun observeAll(): Flow<List<…>>` — `excludedAtMillis DESC`.
- `suspend fun count(): Int`
- `suspend fun isExcluded(trackKey: String): Boolean`
- `suspend fun delete(trackKey: String)`, `suspend fun clear()`

### 9.4 `TasteProfileDao`

- `suspend fun get(): TasteProfileEntity?` — `WHERE id = 1 LIMIT 1`.
- `suspend fun upsert(profile)` — REPLACE.
- `suspend fun clear()`.

---

## 10. External types this cluster compiles against (shape only)

Implementers must not read `:innertube`/`:metadata`; reproduce these shapes as dependencies (they
already exist in those modules — do not redefine them in `:core`):

```kotlin
// com.revenge.music.models (in :core, not part of this cluster's rewrite)
data class MediaMetadata(
  val id: String, val title: String, val artists: List<Artist>, val duration: Int,
  val thumbnailUrl: String? = null, val album: Album? = null, val setVideoId: String? = null,
  val musicVideoType: String? = null, val explicit: Boolean = false, val liked: Boolean = false,
  val likedDate: LocalDateTime? = null, val inLibrary: LocalDateTime? = null,
  val libraryAddToken: String? = null, val libraryRemoveToken: String? = null,
  val suggestedBy: String? = null, val source: QueueItemSource = QueueItemSource.USER,
) { data class Artist(val id: String?, val name: String)
    data class Album(val id: String, val title: String) }

fun MediaMetadata.toSongEntity(): SongEntity
fun SongItem.toMediaMetadata(): MediaMetadata

// com.revenge.innertube.models
class SongItem(id, title, artists: List<Artist>, album: Album? = null, duration: Int? = null,
  musicVideoType: String? = null, …, thumbnail: String, explicit: Boolean = false,
  setVideoId: String? = null, libraryAddToken: String? = null, libraryRemoveToken: String? = null, …)
class AlbumItem(browseId, playlistId: String, id = browseId, title, artists: List<Artist>?,
  year: Int? = null, thumbnail: String, explicit: Boolean = false, description: String? = null)
class ArtistItem(id, title, thumbnail: String?, …)
class PlaylistItem(id, title, author: Artist?, songCountText: String?, thumbnail: String?,
  playEndpoint: WatchEndpoint?, shuffleEndpoint: WatchEndpoint?, radioEndpoint: WatchEndpoint?,
  isEditable: Boolean = false, …)
class Artist(val name: String, val id: String?)   // innertube Artist
class WatchEndpoint { val params: String? }

// com.revenge.innertube.pages
data class AlbumPage(album: AlbumItem, songs: List<SongItem>, otherVersions: List<AlbumItem>,
  releasesForYou: List<AlbumItem> = emptyList(), description: String? = null, …)
data class ArtistPage(artist: ArtistItem, sections: List<ArtistSection>, description: String?,
  subscriberCountText: String?, …)

// com.revenge.metadata
object MetadataCleaner { fun clean(title: String, artist: String): CleanedMetadata }
class CleanedMetadata { fun toDisplayTitle(): String; val primaryArtist: String }

// com.revenge.music.constants (in :core; must exist)
enum class SongSortType { CREATE_DATE, NAME, ARTIST, PLAY_TIME }
enum class ArtistSongSortType { CREATE_DATE, NAME, PLAY_TIME }
enum class ArtistSortType { CREATE_DATE, NAME, SONG_COUNT, PLAY_TIME }
enum class AlbumSortType { CREATE_DATE, NAME, ARTIST, YEAR, SONG_COUNT, LENGTH, PLAY_TIME }
enum class PlaylistSortType { CREATE_DATE, NAME, SONG_COUNT, LAST_UPDATED }
```

---

## 11. Acceptance tests

Tests use JUnit4 + Robolectric where Android APIs are needed, and Room's
`MigrationTestHelper` against the checked-in schemas. All cases are deterministic (fixed clock /
seeded randomness / inserted fixtures).

### 11.1 Schema and migration
1. **Fresh install** — build the database at v48 on an empty file; `PRAGMA user_version` is 48; table
   list equals the 21 entities; the three views exist; opening again performs no migration.
2. **Full upgrade chain** — for every start version `v ∈ {1,2,…,47}` (at least 1, 21, 27, 28, 30, 36,
   41, 44, 45, 46), create a database with the schema for `v`, insert representative rows, migrate to
   48, and assert: no exception; expected rows survive with the values the specs above mandate; all
   indices/views exist; `PRAGMA foreign_key_check` is empty.
3. **1→2 id remap** — old integer artist/playlist ids are replaced by strings matching
   `LA…`/`LP…`; every old song still has exactly one artist via `song_artist_map`; playlist positions
   are gap-free starting at 0 after normalization of duplicates.
4. **27→28 column backfill** — databases created at 27 with and without `lyricsOffset`/`isVideo`
   both migrate; values default to 0 when the column was absent.
5. **Idempotent guards** — 44→45 and 45→46 on a DB that already has `isLocal`/`hideFromQuickPicks`
   must not throw and must not duplicate columns.
6. **46→47 stats seeding** — with two songs and three events (two for song A, one for song B),
   after migration `song_play_stats` contains one row per `lowercase(title|artist)` key with summed
   playtime, playCount = event count, skipCount 0, lastPlayedAtMillis = max timestamp, videoId = song id,
   artworkUrl = thumbnail; events with `playTime = 0` produce no row.
7. **47→48 taste profile rebuild** — a legacy table with a `genres` column is dropped and recreated
   with `topArtistsJson` etc.; a table already in the new shape is left byte-for-byte unchanged.

### 11.2 Converters
8. Round-trip of `2024-02-29T13:45:07.123` and `1970-01-01T00:00:00` through
   `dateToTimestamp`/`fromTimestamp` is identity; `null` stays `null`.

### 11.3 Song/relations
9. Insert a song with two artists and one album; `song(id)` emits one `Song` whose `artists` are in
   `SongArtistMap.position` order and whose `album` is the mapped album.
10. `Song.thumbnailUrl` for a local song with `albumId = "LOCAL_ALBUM_42"` is
    `content://media/external/audio/albumart/42`; with no parsable id it falls back to
    `thumbnailUrl ?: id`.
11. Deleting a song cascades its `song_artist_map`, `song_album_map`, `playlist_song_map`, `event`,
    `related_song_map` rows.

### 11.4 Playlist ordering
12. `addSongToPlaylist` appended twice yields positions 0,1,2…; adding when other inserts happened
    still starts at `MAX(position)+1` (not at `playlist.songCount`).
13. `move(playlistId, from = 0, to = 2)` on positions 0..4 results in positions 1,2,0,3,4 (row at
    `from` lands at `to`, intervening rows shift by one); `move(x, 2, 2)` is a no-op.
14. `playlists(...)` returns pinned playlists before unpinned ones for every sort type and direction.

### 11.5 Recommendation and hidden songs
15. `quickPicks(now, randomSeed = 7)` called twice returns the same order; a song with
    `hideFromQuickPicks = 1` never appears; the union deduplicates a song that is both recent and random.
16. `forgottenFavorites` returns only songs whose old playtime exceeds 5× recent playtime and excludes
    hidden songs.

### 11.6 Playback state survival (writes from the player)
17. Simulate the player's listener: `incrementTotalPlayTime(songId, 30_000)` and
    `insert(Event(songId, now, 30_000))` inside `database.query { }`; `events()` emits the new event
    newest-first, `mostPlayedSongs` includes the song, and the values survive a `close()` +
    re-open of the same file.
18. `incrementPlayCount(songId)` twice for a fixed UTC month yields `count = 2`; calling it for a
    different month creates a second row; `getLifetimePlayCount` sums both.
19. `clearListenHistory()` empties `event` but leaves `song.totalPlayTime` unchanged.

### 11.7 Download recovery
20. `updateDownloadedInfo(id, true, t)` makes the song appear in `downloadedSongsByCreateDateAsc`
    ordered by `dateDownload`/rowid; `updateDownloadedInfo(id, false, null)` removes it. State
    persists across database re-open (recovery after process death).
21. `pruneFormats()` removes formats without a song; `pruneLocalAlbums()`/`pruneLocalArtists()`
    keep only local entities referenced by surviving local songs.

### 11.8 Side DAOs and transactions
22. `SongPlayStatsDao`: upsert keyed by `trackKey` replaces; `mostPlayedSince` respects the cutoff and
    ordering; `observeTopPlayed` emits after an upsert.
23. `RecommendationExclusionDao`: `upsertAll`, `isExcluded`, `delete`, `clear` round-trip;
    `observeAll` emits newest-first.
24. `TasteProfileDao.get()` returns null before any write and the singleton row afterwards;
    `upsert` twice keeps a single row.
25. `SpeedDialDao.isPinned` flips after `insert`/`delete`; `getAll` is ordered by `createDate`.
26. A `withTransaction` block that throws leaves no partial writes; a `database.transaction { }` that
    throws is rolled back and does not propagate to the caller.
27. `checkpoint()` completes without error while a WAL database is open.

### 11.9 Non-applicable clusters
Audio focus, EQ parsing and USB routing are not part of `:core`'s db cluster and are covered by their
own cluster specs. Do not test them here.
