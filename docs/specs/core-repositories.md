# Clean-Room Specification — `core-repositories` cluster (`:core`)

> Required behaviour only. Any algorithm shape is illustrative; implementers must design independently
> and are audited for baseline similarity.

Status: M2 specification (behavioural contract only).
Cluster: `core-repositories`.
Module: `:core` — directory `core`, Gradle path `:core`.
Focus: repositories (domain adapter ports), download/export queue persistence contracts, local media
scanner persistence contracts, backup/restore persistence contract, cache and playback-persistence
services.
Clean-room namespaces that must be preserved exactly:

- `com.revenge.core.repository`
- `com.revenge.core.mapping`
- `com.revenge.music.playback`
- `com.revenge.music.db`, `com.revenge.music.db.entities`, `com.revenge.music.db.daos`
- `com.revenge.music.models`
- `com.revenge.music.utils`, `com.revenge.music.di`

This document describes observable behaviour, API shape, data schemas and invariants. It intentionally
omits statement-level implementation. Any implementation satisfying the contracts and acceptance tests
is acceptable.

---

## 1. Module purpose and consumers

`:core` is the Android-facing infrastructure layer. For this cluster it owns:

1. The two domain-repository implementations that adapt the Room-backed `MusicDatabase` to the pure
   `:domain` ports.
2. The entity-to-domain mapper used only by those adapters.
3. Small cross-layer playback extension interfaces consumed by `:app` and `:lyrics`.
4. The Room database identity, schema, converters, and the facade/DAO surface required by the
   repositories **and by the download, export, scanner, backup and cache services** that live in
   `:app`.
5. Playback-state persistence models (`PersistQueue`, `PersistPlayerState`) and the small persistence
   utilities (DataStore access, playback diagnostics log, cache qualifiers, connectivity, crash
   forwarding).

Known consumers:

- `app/src/main/kotlin/com/revenge/music/di/DomainBindingsModule.kt` binds
  `SongRepositoryImpl` → `SongRepository` and `PlaylistRepositoryImpl` → `PlaylistRepository`.
- `:domain` use cases (`GetQuickPicksUseCase`, `ResolveLocalMixUseCase`) consume only the repository
  ports, never the implementations.
- `:lyrics` implements `ILyricsHelper` and constructs `LyricsWithProvider`.
- `app` player, widget, cast and download code consumes the playback interfaces and the database
  facade/DAO.
- `app` service code (`DownloadUtil`, `ExportTaskQueue`, `LocalSongScanner`,
  `BackupRestoreViewModel`, `MusicService`) consumes the persistence contracts in §6–§7.

The download/export/scanner/backup service classes themselves remain in `:app` (they require
app-only dependencies such as media3-offline, Coil and Hilt Android). This cluster specifies the
persistence and model contract they depend on, not their UI wiring.

---

## 2. Module/build contract

Preserve from `core/build.gradle.kts`:

- Android library plugin, KSP plugin, Kotlin serialization plugin.
- `namespace = "com.revenge.core"`, `compileSdk = 37`, `minSdk = 26`,
  Java/Kotlin source and target `21`, core-library desugaring enabled, `jvmToolchain(21)`.
- KSP argument `room.schemaLocation = "$projectDir/schemas"`; schema JSONs are committed under
  `core/schemas/com.revenge.music.db.InternalDatabase/` (current committed file `48.json`).
- Project dependencies exposed as `api`: `:innertube`, `:metadata`, `:domain`.
- Third-party `api` dependencies required by public signatures: Room runtime/ktx + KSP compiler,
  DataStore preferences, coroutines, Compose runtime/ui/animation, Timber, Ktor client core + JSON,
  Apache Commons Lang3, protobuf-javalite, media3 + media3-session, `javax.inject`, AndroidX core-ktx.
- Tests: JUnit 4, `kotlinx-coroutines-test`, Robolectric.

A symbol referenced from another module must be reproduced here if the implementer cannot read that
module: only `:domain` shapes are reproduced (§3.4). Innertube/metadata types are dependencies, not
reimplemented.

---

## 3. Repository contracts

### 3.1 `com.revenge.core.repository.SongRepositoryImpl`

```kotlin
@Singleton
class SongRepositoryImpl @Inject constructor(
  private val database: MusicDatabase,
) : SongRepository
```

Observable behaviour:

| Method | Contract |
|---|---|
| `getSong(id: String): Flow<Song?>` | Emits the current row for `id` and re-emits on any change to `song` or its relations. Emits `null` when no row exists (and for `id` values that never existed). The flow is cold until collected. |
| `getLikedSongs(): Flow<List<Song>>` | Emits all `song` rows with `liked = 1`, ordered by insertion order (SQLite `rowId` ascending, stable). Emitted songs have populated `artists`. Deletions/updates re-emit. Empty DB ⇒ empty list. |
| `getRecentlyPlayed(limit: Int): Flow<List<Song>>` | Emits distinct songs in most-recently-played-first order, truncated to `limit`. Each song appears once, at the position of its most recent `event`; older events for the same domain `Song.id` are collapsed into that occurrence. `limit <= 0` ⇒ empty list. Fewer distinct songs than `limit` ⇒ all of them. Events reference existing songs (FK cascade removes orphans), so no null handling is observable. |
| `updateLiked(id: String, isLiked: Boolean)` | Fire-and-forget write on the database query executor (see §6.1). Sets `liked = isLiked`; when `isLiked` is `true` sets `likedDate` to `LocalDateTime.now()` (UTC-naive local timestamp), when `false` sets `likedDate = NULL`. Unknown `id` ⇒ no-op. Returns before the write necessarily completes; the write itself is guaranteed to be enqueued. |
| `incrementPlayTime(id: String, durationMs: Long)` | Fire-and-forget; increases the matching song row's `totalPlayTime` by `durationMs`. Negative values are accepted and subtract. Unknown `id` ⇒ no-op. No clamping, no event row is written. |

Ordering/threading: all reads are Room `Flow`s executed on Room's query executor. The two write
methods return immediately and run their block asynchronously on the database query executor; they
must not block the caller. Callers that need the write to be visible before continuing use
`database.withTransaction` instead (as the scanner/download code does).

### 3.2 `com.revenge.core.repository.PlaylistRepositoryImpl`

```kotlin
@Singleton
class PlaylistRepositoryImpl @Inject constructor(
  private val database: MusicDatabase,
) : PlaylistRepository
```

| Method | Contract |
|---|---|
| `getAllPlaylists(): Flow<List<Playlist>>` | Emits playlists with `bookmarkedAt IS NOT NULL`, ordered by `name` ascending (SQLite binary collation). `songCount` is the number of `playlist_song_map` rows for the playlist. The domain thumbnail is `playlist.thumbnailUrl` when non-null, otherwise the first non-null `song.thumbnailUrl` among the preview songs (positions ≤ 3), otherwise `null`. Re-emits on playlist or map changes. Empty ⇒ empty list. |
| `getPlaylistSongs(playlistId: String): Flow<List<Song>>` | Emits the songs mapped in `playlist_song_map` for `playlistId`, ordered by `position` ascending, each with populated `artists`. Unknown/empty playlist ⇒ empty list. Duplicate `(playlistId, songId)` rows, if present, produce duplicate songs. |
| `suspend addSongToPlaylist(playlistId: String, songId: String)` | Appends one membership at the position after the playlist's current maximum position, or `0` when the playlist has no memberships, with `setVideoId = null`. The write is lenient: constraint violations are swallowed and callers cannot observe them. No uniqueness rule prevents adding the same song twice. Returns before completion. Concurrent calls for one playlist are not serialised, so callers must not rely on atomic gap-free positions or a unique next position. FK rules mean an unknown `playlistId`/`songId` leaves no row and no observable error. |
| `suspend removeSongFromPlaylist(playlistId: String, songId: String)` | Removes **every** membership matching `(playlistId, songId)`, including duplicates; other playlists' memberships are untouched. Remaining positions are **not** renumbered (gaps persist). Unknown pair ⇒ no-op. Returns before completion. |

Both write methods must enqueue work on the DB executor; they must not run a blocking DB call on the
caller's thread.

### 3.3 Shared repository invariants

- Constructed only through `@Inject`; no additional constructor parameters.
- `database` is an `MusicDatabase` facade, not `InternalDatabase`.
- No caching, memoisation, retry or exception translation in the adapters. Room exceptions propagate
  from flow collection; write helpers swallow constraint failures as described.
- Mapping is pure and stateless (§4).
- The domain expressions are structural equality data classes; list order is preserved.

### 3.4 Reproduced `:domain` contracts (compile without reading `:domain`)

```kotlin
package com.revenge.domain.models

data class Song(
  val id: String,
  val title: String,
  val artists: List<ArtistRef>,
  val album: AlbumRef?,
  val durationSeconds: Int,
  val thumbnailUrl: String?,
  val isExplicit: Boolean = false,
  val isLocal: Boolean = false,
  val liked: Boolean = false,
  val totalPlayTimeMs: Long = 0L,
)

data class ArtistRef(val id: String, val name: String)
data class AlbumRef(val id: String, val title: String)
data class Playlist(
  val id: String,
  val name: String,
  val songCount: Int = 0,
  val thumbnailUrl: String? = null,
)
```

```kotlin
package com.revenge.domain.repositories

interface SongRepository {
  fun getSong(id: String): Flow<Song?>
  fun getLikedSongs(): Flow<List<Song>>
  fun getRecentlyPlayed(limit: Int): Flow<List<Song>>
  suspend fun updateLiked(id: String, isLiked: Boolean)
  suspend fun incrementPlayTime(id: String, durationMs: Long)
}

interface PlaylistRepository {
  fun getAllPlaylists(): Flow<List<Playlist>>
  fun getPlaylistSongs(playlistId: String): Flow<List<Song>>
  suspend fun addSongToPlaylist(playlistId: String, songId: String)
  suspend fun removeSongFromPlaylist(playlistId: String, songId: String)
}
```

Exact parameter names and types are part of the contract because call sites use named arguments and
DI checks the override signature.

---

## 4. Mapping contract — `com.revenge.core.mapping.DomainMappers`

Four top-level functions. Both DB `Song` (the Room relation model) and `SongEntity` (the flat entity)
map to the same domain type, so overload resolution by receiver type is load-bearing.

| Function | Behaviour |
|---|---|
| `fun com.revenge.music.db.entities.Song.toDomain(): com.revenge.domain.models.Song` | `id/title` from `song.id/song.title`; `artists` = relation artists mapped in relation order to `ArtistRef(id, name)`; `album` = relation album `AlbumRef(id,title)` when present, else `song.albumId` with `title = song.albumName.orEmpty()`; `durationSeconds = song.duration` (may be `-1`); `thumbnailUrl = thumbnailUrl` (the relation getter, which for local songs falls back to a MediaStore album-art URI or the song id); `isExplicit = song.explicit`; `isLocal = song.isLocal`; `liked = song.liked`; `totalPlayTimeMs = song.totalPlayTime`. |
| `fun com.revenge.music.db.entities.SongEntity.toDomain(): com.revenge.domain.models.Song` | Same scalars from the flat row. **`artists` is always `emptyList()`** (this mapper has no relation data). `album = albumId?.let { AlbumRef(it, albumName.orEmpty()) }`. Thumbnail resolution order: (1) non-null `thumbnailUrl`; (2) if `isLocal`, parse `albumId` by removing prefix `"LOCAL_ALBUM_"` to a `Long`; if parse succeeds and value `> 0`, use `"content://media/external/audio/albumart/<id>"`, otherwise use the song `id` (which is a content URI for local tracks); (3) otherwise `null`. |
| `fun com.revenge.music.db.entities.Playlist.toDomain(): com.revenge.domain.models.Playlist` | `id/name`; `songCount` from the relation's computed count; thumbnail is `playlist.thumbnailUrl` when non-null, otherwise the first non-null thumbnail among the preview songs (positions ≤ 3), otherwise `null`. |
| `fun com.revenge.music.db.entities.PlaylistEntity.toDomain(songCount: Int = 0): com.revenge.domain.models.Playlist` | `id/name/thumbnailUrl` copied; `songCount` = supplied value (default `0`). |

No normalisation (no trimming, no blank-to-null). Values pass through unchanged except the local
thumbnail derivation above.

---

## 5. Playback extension interfaces — `com.revenge.music.playback`

These are the public extension points. Implementations live in `:app`/`:lyrics`; the interfaces must
keep exact FQCN, member names, parameter names, parameter order and defaults.

### 5.1 `CastDeviceKind`

```kotlin
enum class CastDeviceKind { TV, SPEAKER, CHROMECAST, UNKNOWN }
```

Companion function:

```kotlin
fun fromName(name: String, description: String?): CastDeviceKind
```

Semantics (classification precedence is fixed; matching is case-insensitive; a null `description` is
treated as an empty model string):

1. `TV` if device name or model contains `"tv"`.
2. else `SPEAKER` if device name contains `"speaker"`, `"home"` or `"nest"`, or model contains
   `"speaker"`.
3. else `CHROMECAST` if device name or model contains `"chromecast"`.
4. else `UNKNOWN`.

`fromName` never throws; empty strings classify as `UNKNOWN`. This is also the routing fallback: an
unrecognised device is `UNKNOWN`, which downstream cast UI maps to its generic bucket.

### 5.2 `ILyricsHelper` and `LyricsWithProvider`

```kotlin
data class LyricsWithProvider(val lyrics: String?, val providerName: String)

interface ILyricsHelper {
  suspend fun getLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider
}
```

- `lyrics == null` means "not found"; consumers substitute `""`. A non-null value is the raw lyrics
  text (may contain sync metadata/timestamps).
- `providerName` is always non-null; the conventional not-found value is `"Unknown"`.
- `getLyrics` may suspend on network; it must not throw for "not found" (return the not-found shape)
  and must be safe to call from UI collectors. Cancellation propagates.
- `MediaMetadata` is the `:core` model reproduced in §6.9; `com.revenge.music.models.MediaMetadata`.

### 5.3 `ISyncUtils`

```kotlin
interface ISyncUtils {
  fun likeSong(song: SongEntity)
}
```

Non-suspending, fire-and-forget: enqueues a like synchronisation for `song` and returns immediately.
Must not block the caller or throw synchronously. `song` is
`com.revenge.music.db.entities.SongEntity`.

### 5.4 `IWidgetManager`

```kotlin
interface IWidgetManager {
  suspend fun updateWidgets(
    title: String,
    artist: String,
    artworkUri: String?,
    isPlaying: Boolean,
    isLiked: Boolean,
    duration: Long = 0,
    currentPosition: Long = 0,
  )
}
```

Pushes a playback snapshot to all home-screen widgets. Suspending; may perform I/O. `artworkUri`
nullable; `duration`/`currentPosition` default to `0` so callers may omit them. Implementations must
tolerate being called with blank title/artist without throwing.

### 5.5 `IYTPlayerUtils`

```kotlin
interface IYTPlayerUtils {
  suspend fun getStreamInfo(videoId: String, context: android.content.Context): Any?
  suspend fun getAudioConfig(videoId: String): Any?
}
```

Both suspend and nullable-returning. `getStreamInfo` takes an Android `Context`; `getAudioConfig`
does not. `null` denotes "unavailable". The `Any?` return types are intentional (media3/innertube
opaque payloads) and must not be narrowed at the interface boundary. Failures are conveyed by the
implementation (exception or `null`), not by this interface.

---

## 6. Persistence and cache contracts

### 6.1 `com.revenge.music.db.MusicDatabase` — facade

The facade is a class (not the Room database itself) that exposes the full `DatabaseDao` surface as
if it were the DAO. How that exposure is achieved is not normative.

```kotlin
class MusicDatabase(private val delegate: InternalDatabase) : DatabaseDao {
  // All DatabaseDao members are directly callable on the facade.
  val speedDialDao: SpeedDialDao
  val songPlayStatsDao: SongPlayStatsDao
  val recommendationExclusionDao: RecommendationExclusionDao
  val tasteProfileDao: TasteProfileDao
  val openHelper: androidx.sqlite.db.SupportSQLiteOpenHelper

  fun query(block: MusicDatabase.() -> Unit)
  fun transaction(block: MusicDatabase.() -> Unit)
  suspend fun withTransaction(block: suspend MusicDatabase.() -> Unit)
  fun close()
}
```

Behavioural contract:

- `query(block)` enqueues `block` on Room's **query executor** and returns `Unit` immediately.
  `block`'s receiver is the `MusicDatabase` facade, so all DAO methods are callable inside.
  Exceptions inside the block are not rethrown to the caller.
- `transaction(block)` enqueues a single transaction on the **transaction executor** and returns
  immediately; exceptions are not rethrown.
- `withTransaction(block)` suspends until the transaction completes and propagates exceptions. It is
  the only API that guarantees atomicity and completion for multi-statement writes.
- `close()` closes the delegate.
- Room database identity (hard compatibility):
  - `InternalDatabase.DB_NAME = "song.db"`.
  - `@Database(version = 48, exportSchema = true)`, identity hash
    `04eaba22573a4ef29ffee7f776038a84` (schema file
    `core/schemas/com.revenge.music.db.InternalDatabase/48.json`).
  - `@TypeConverters(Converters::class)`.
  - Entities and views exactly as §6.4.
- Builder behaviour: WAL journal mode; fixed-size transaction and query executors (pool size is an
  implementation choice, not part of the contract); on open, apply `PRAGMA busy_timeout = 60000`,
  `PRAGMA cache_size = -16000`, `PRAGMA wal_autocheckpoint = 1000`, `PRAGMA synchronous = NORMAL`.
  A failure to apply these PRAGMAs is logged, not fatal.

Consumers inside this cluster only use the exposed DAO methods and the helpers above.

### 6.2 `com.revenge.music.db.Converters`

```kotlin
class Converters {
  @TypeConverter fun fromTimestamp(value: Long?): LocalDateTime?
  @TypeConverter fun dateToTimestamp(date: LocalDateTime?): Long?
}
```

- Timestamps are UTC epoch milliseconds, interpreted with `ZoneOffset.UTC` in both directions.
- `null` maps to `null` in both directions.
- Round-trip must preserve the exact `LocalDateTime` for values with milliseconds (test: 2024-02-29
  13:45:07.123).
- No other converter exists; all other persisted fields are primitives/strings.

### 6.3 Required `DatabaseDao` surface

`com.revenge.music.db.DatabaseDao` is a Room `@Dao` interface. The following methods (exact names,
types, nullability) are consumed by this cluster and by downstream services and must exist.

Reads used by repositories:

```kotlin
@Transaction fun song(songId: String?): Flow<Song?>
@Transaction fun likedSongsByRowIdAsc(): Flow<List<Song>>          // WHERE liked ORDER BY rowId
@Transaction fun events(): Flow<List<EventWithSong>>               // ORDER BY rowId DESC
@Transaction fun playlistsByNameAsc(): Flow<List<Playlist>>        // bookmarkedAt IS NOT NULL ORDER BY name
@Transaction fun playlistSongs(playlistId: String): Flow<List<PlaylistSong>> // ORDER BY position
@Transaction fun playlistSongMaps(songId: String): List<PlaylistSongMap>
@Transaction fun playlistSongMaps(playlistId: String, from: Int): List<PlaylistSongMap> // position >= from ORDER BY position
@Transaction fun getSongById(songId: String): Song?
@Transaction fun getSongByIdBlocking(songId: String): Song?
@Transaction suspend fun getSongsByIds(songIds: List<String>): List<Song>
@Transaction fun getSongsByIdsFlow(songIds: List<String>): Flow<List<Song>>
@Transaction suspend fun getArtistEntitiesByIds(ids: List<String>): List<ArtistEntity>
@Transaction suspend fun getAlbumEntitiesByIds(ids: List<String>): List<AlbumEntity>
@Query("SELECT id FROM song WHERE isLocal = 1") suspend fun localSongIds(): List<String>
```

Writes used by repositories:

```kotlin
@Insert(onConflict = IGNORE) fun insert(song: SongEntity): Long          // -1 on conflict
@Delete fun delete(playlistSongMap: PlaylistSongMap)
@Insert(onConflict = IGNORE) fun insertInternal(map: PlaylistSongMap)
fun insert(map: PlaylistSongMap)                                         // Unit; swallows exceptions
@Query fun updateLikedStatus(songId: String, liked: Boolean, likedDate: LocalDateTime?)
@Query fun incrementTotalPlayTime(songId: String, playTime: Long)
```

Writes used by download/scan/backup paths:

```kotlin
@Query fun updateDownloadedInfo(songId: String, downloaded: Boolean, date: LocalDateTime?)
@Insert(onConflict = IGNORE) fun insert(artist: ArtistEntity)
@Insert(onConflict = IGNORE) fun insert(album: AlbumEntity): Long
@Insert(onConflict = IGNORE) fun insert(playlist: PlaylistEntity)
fun insert(map: SongArtistMap)      // lenient wrapper, Unit, swallows
fun insert(map: SongAlbumMap)       // lenient wrapper, Unit, swallows
fun insert(map: AlbumArtistMap)     // lenient wrapper, Unit, swallows
@Insert(onConflict = IGNORE) fun insert(event: Event)
@Upsert fun upsert(song: SongEntity)
@Upsert fun upsert(format: FormatEntity)
@Upsert fun upsert(lyrics: LyricsEntity)
@Upsert fun upsert(beatInfo: BeatInfoEntity)
@Query fun deleteSongArtistMaps(songId: String)
@Query fun deleteSongAlbumMaps(songId: String)
@Query fun deleteAlbumArtistMapsByAlbumIds(albumIds: List<String>)
@Query fun deleteSongsByIds(songIds: List<String>)
@Query fun clearLocalSongs()
@Query fun pruneLocalAlbums()
@Query fun pruneLocalArtists()
@Query fun pruneFormats()
@Query fun prunePlayCounts()
```

Also required because the scanner/backup paths touch them: `insertRemovedPlaylistSongMaps`-style
operations are not required beyond the above; deletion of a `SongEntity` cascades to maps/events via
foreign keys. `checkpoint()` performs `PRAGMA wal_checkpoint(FULL)`.

Semantics of lenient wrappers: constraint violations are swallowed — the method returns `Unit` and
callers cannot observe failures. `insert(song)` returns the new rowId, or `-1` when the primary key
already exists (so callers can distinguish insert-vs-update).

`musicVideoType`/`QueueItemSource` models needed by scanner/download metadata:
`com.revenge.music.models.MediaMetadata` is reproduced in §6.9.

### 6.4 Database schema v48 (must be preserved for upgrade compatibility)

All tables below are `@Entity` classes; all views are `@DatabaseView`. Column types are SQLite
affinities as emitted by Room. `NOT NULL` is noted explicitly; absent means nullable.

**`song`** — PK `id` TEXT; index `index_song_albumId (albumId)`

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT | NOT NULL, PK |
| `title` | TEXT | NOT NULL |
| `duration` | INTEGER | NOT NULL |
| `thumbnailUrl` | TEXT | |
| `albumId` | TEXT | |
| `albumName` | TEXT | |
| `explicit` | INTEGER | NOT NULL DEFAULT 0 |
| `year` | INTEGER | |
| `date` | INTEGER | |
| `dateModified` | INTEGER | |
| `liked` | INTEGER | NOT NULL |
| `likedDate` | INTEGER | |
| `totalPlayTime` | INTEGER | NOT NULL |
| `inLibrary` | INTEGER | |
| `dateDownload` | INTEGER | |
| `isLocal` | INTEGER | NOT NULL DEFAULT false |
| `libraryAddToken` | TEXT | |
| `libraryRemoveToken` | TEXT | |
| `lyricsOffset` | INTEGER | NOT NULL DEFAULT 0 |
| `romanizeLyrics` | INTEGER | NOT NULL DEFAULT true |
| `isDownloaded` | INTEGER | NOT NULL DEFAULT 0 |
| `isUploaded` | INTEGER | NOT NULL DEFAULT false |
| `isVideo` | INTEGER | NOT NULL DEFAULT false |
| `hideFromQuickPicks` | INTEGER | NOT NULL DEFAULT 0 |

**`artist`** — PK `id` TEXT: `id` TEXT NOT NULL; `name` TEXT NOT NULL; `thumbnailUrl` TEXT;
`channelId` TEXT; `lastUpdateTime` INTEGER NOT NULL; `bookmarkedAt` INTEGER;
`isLocal` INTEGER NOT NULL DEFAULT false.

**`album`** — PK `id` TEXT: `id` TEXT NOT NULL; `playlistId` TEXT; `title` TEXT NOT NULL; `year`
INTEGER; `thumbnailUrl` TEXT; `themeColor` INTEGER; `songCount` INTEGER NOT NULL; `duration` INTEGER
NOT NULL; `explicit` INTEGER NOT NULL DEFAULT 0; `lastUpdateTime` INTEGER NOT NULL;
`bookmarkedAt` INTEGER; `likedDate` INTEGER; `inLibrary` INTEGER; `description` TEXT;
`isLocal` INTEGER NOT NULL DEFAULT false; `isUploaded` INTEGER NOT NULL DEFAULT false.

**`playlist`** — PK `id` TEXT: `id` TEXT NOT NULL; `name` TEXT NOT NULL; `browseId` TEXT;
`createdAt` INTEGER; `lastUpdateTime` INTEGER; `isEditable` INTEGER NOT NULL DEFAULT true;
`bookmarkedAt` INTEGER; `remoteSongCount` INTEGER; `playEndpointParams` TEXT; `thumbnailUrl` TEXT;
`shuffleEndpointParams` TEXT; `radioEndpointParams` TEXT; `isLocal` INTEGER NOT NULL DEFAULT false;
`isAutoSync` INTEGER NOT NULL DEFAULT false; `isPinned` INTEGER NOT NULL DEFAULT false.

**`song_artist_map`** — composite PK `(songId, artistId)`; indices
`index_song_artist_map_songId(songId)`, `index_song_artist_map_artistId(artistId)`; FKs
`songId → song.id CASCADE`, `artistId → artist.id CASCADE`. Columns: `songId` TEXT NOT NULL,
`artistId` TEXT NOT NULL, `position` INTEGER NOT NULL.

**`song_album_map`** — composite PK `(songId, albumId)`; indices
`index_song_album_map_songId(songId)`, `index_song_album_map_albumId(albumId)`; FKs
`songId → song.id CASCADE`, `albumId → album.id CASCADE`. Columns: `songId` TEXT NOT NULL,
`albumId` TEXT NOT NULL, `index` INTEGER NOT NULL.

**`album_artist_map`** — composite PK `(albumId, artistId)`; indices
`index_album_artist_map_albumId(albumId)`, `index_album_artist_map_artistId(artistId)`; FKs
`albumId → album.id CASCADE`, `artistId → artist.id CASCADE`. Columns: `albumId` TEXT NOT NULL,
`artistId` TEXT NOT NULL, `order` INTEGER NOT NULL.

**`playlist_song_map`** — PK `id` INTEGER AUTOINCREMENT NOT NULL; indices
`index_playlist_song_map_playlistId(playlistId)`, `index_playlist_song_map_songId(songId)`; FKs
`playlistId → playlist.id CASCADE`, `songId → song.id CASCADE`. Columns: `id` INTEGER NOT NULL,
`playlistId` TEXT NOT NULL, `songId` TEXT NOT NULL, `position` INTEGER NOT NULL, `setVideoId` TEXT.
**No unique constraint on `(playlistId, songId)`** — duplicates are legal.

**`search_history`** — PK `id` INTEGER AUTOINCREMENT NOT NULL; unique index
`index_search_history_query(query)`. Columns: `id` INTEGER NOT NULL, `query` TEXT NOT NULL.

**`format`** — PK `id` TEXT. Columns: `id` TEXT NOT NULL; `itag` INTEGER NOT NULL; `mimeType` TEXT
NOT NULL; `codecs` TEXT NOT NULL; `bitrate` INTEGER NOT NULL; `sampleRate` INTEGER;
`contentLength` INTEGER NOT NULL; `loudnessDb` REAL; `perceptualLoudnessDb` REAL; `playbackUrl` TEXT.

**`lyrics`** — PK `id` TEXT. Columns: `id` TEXT NOT NULL; `lyrics` TEXT NOT NULL; `provider` TEXT
NOT NULL DEFAULT `'Unknown'`; `translatedLyrics` TEXT NOT NULL DEFAULT `''`;
`translationLanguage` TEXT NOT NULL DEFAULT `''`; `translationMode` TEXT NOT NULL DEFAULT `''`.

**`event`** — PK `id` INTEGER AUTOINCREMENT NOT NULL; index `index_event_songId(songId)`; FK
`songId → song.id CASCADE`. Columns: `id` INTEGER NOT NULL, `songId` TEXT NOT NULL,
`timestamp` INTEGER NOT NULL, `playTime` INTEGER NOT NULL.

**`related_song_map`** — PK `id` INTEGER AUTOINCREMENT NOT NULL; indices
`index_related_song_map_songId(songId)`, `index_related_song_map_relatedSongId(relatedSongId)`; FKs
`songId → song.id CASCADE`, `relatedSongId → song.id CASCADE`. Columns: `id` INTEGER NOT NULL,
`songId` TEXT NOT NULL, `relatedSongId` TEXT NOT NULL.

**`set_video_id`** — PK `videoId` TEXT. Columns: `videoId` TEXT NOT NULL, `setVideoId` TEXT.

**`playCount`** — composite PK `(song, year, month)`. Columns: `song` TEXT NOT NULL, `year` INTEGER
NOT NULL, `month` INTEGER NOT NULL, `count` INTEGER NOT NULL.

**`recognition_history`** — PK `id` INTEGER AUTOINCREMENT NOT NULL; index
`index_recognition_history_trackId(trackId)`. Columns: `id` INTEGER NOT NULL, `trackId` TEXT NOT NULL,
`title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT, `coverArtUrl` TEXT, `coverArtHqUrl`
TEXT, `genre` TEXT, `releaseDate` TEXT, `label` TEXT, `shazamUrl` TEXT, `appleMusicUrl` TEXT,
`spotifyUrl` TEXT, `isrc` TEXT, `youtubeVideoId` TEXT, `recognizedAt` INTEGER NOT NULL,
`liked` INTEGER NOT NULL.

**`speed_dial_item`** — PK `id` TEXT. Columns: `id` TEXT NOT NULL, `secondaryId` TEXT, `title` TEXT
NOT NULL, `subtitle` TEXT, `thumbnailUrl` TEXT, `type` TEXT NOT NULL, `explicit` INTEGER NOT NULL,
`createDate` INTEGER NOT NULL.

**`beat_info`** — PK `songId` TEXT. Columns: `songId` TEXT NOT NULL, `bpm` REAL NOT NULL,
`firstBeatOffsetMs` INTEGER NOT NULL, `confidence` REAL NOT NULL, `analyzedAt` INTEGER NOT NULL,
`mixInPointMs` INTEGER, `mixOutPointMs` INTEGER, `keyPitchClass` INTEGER, `keyIsMinor` INTEGER.

**`song_play_stats`** — PK `trackKey` TEXT. Columns: `trackKey` TEXT NOT NULL, `title` TEXT NOT NULL,
`artist` TEXT NOT NULL, `videoId` TEXT, `artworkUrl` TEXT, `totalPlayTimeMs` INTEGER NOT NULL,
`playCount` INTEGER NOT NULL, `skipCount` INTEGER NOT NULL, `lastPlayedAtMillis` INTEGER NOT NULL.

**`recommendation_exclusions`** — PK `trackKey` TEXT. Columns: `trackKey` TEXT NOT NULL,
`excludedAtMillis` INTEGER NOT NULL, `trackName` TEXT NOT NULL, `artistName` TEXT NOT NULL.

**`taste_profile`** — PK `id` INTEGER. Columns: `id` INTEGER NOT NULL, `topArtistsJson` TEXT NOT NULL,
`topTracksJson` TEXT NOT NULL, `topGenresJson` TEXT NOT NULL, `confidence` REAL NOT NULL,
`updatedAtMillis` INTEGER NOT NULL.

**Views:**

- `sorted_song_artist_map` = `SELECT * FROM song_artist_map ORDER BY position`.
- `sorted_song_album_map` = `SELECT * FROM song_album_map ORDER BY \`index\``.
- `playlist_song_map_preview` = `SELECT * FROM playlist_song_map WHERE position <= 3 ORDER BY position`.

Room relation models (not tables) that must exist with these shapes:

- `Song(@Embedded song: SongEntity, artists: List<ArtistEntity>, album: AlbumEntity? = null, format: FormatEntity? = null) : LocalItem` — artists via `SortedSongArtistMap` junction; album via `SongAlbumMap`; `id/title/thumbnailUrl` overrides. Its `thumbnailUrl` getter for local songs returns the MediaStore album-art URI when `albumId` parses as `LOCAL_ALBUM_<n>` (`n > 0`), else `song.thumbnailUrl ?: song.id`; non-local returns `song.thumbnailUrl`.
- `Playlist(@Embedded playlist: PlaylistEntity, songCount: Int, songThumbnails: List<String?>) : LocalItem` with `thumbnails` getter described in §4.
- `PlaylistSong(@Embedded map: PlaylistSongMap, song: Song)`.
- `EventWithSong(@Embedded event: Event, song: Song)`.
- `Album(@Embedded album: AlbumEntity, artists: List<ArtistEntity> = emptyList(), songCountListened: Int? = 0, timeListened: Long? = 0) : LocalItem`.
- `LocalItem` sealed class with abstract `id: String`, `title: String`, `thumbnailUrl: String?`.
- `SortedSongArtistMap`, `SortedSongAlbumMap`, `PlaylistSongMapPreview` are the Room view models.

### 6.5 Migration chain (IDs and required effects)

`InternalDatabase` must declare exactly these `@AutoMigration`s:

`2→3, 3→4, 4→5, 5→6 (spec Migration5To6), 6→7 (Migration6To7), 7→8 (Migration7To8), 8→9, 9→10
(Migration9To10), 10→11 (Migration10To11), 11→12 (Migration11To12), 12→13 (Migration12To13),
13→14 (Migration13To14), 14→15, 15→16, 16→17 (Migration16To17), 17→18, 18→19 (Migration18To19),
19→20 (Migration19To20), 20→21 (Migration20To21), 21→22 (Migration21To22), 22→23 (Migration22To23),
23→24 (Migration23To24), 24→25, 25→26, 26→27, 27→28, 30→31, 32→33, 33→34, 34→35, 35→36,
36→37 (Migration36To37Spec), 41→42 (Migration41To42), 46→47 (Migration46To47Spec),
47→48 (Migration47To48Spec)`.

Builder-added manual migrations, exactly:

`MIGRATION_1_2, MIGRATION_21_24, MIGRATION_22_24, MIGRATION_24_25, MIGRATION_27_28,
MIGRATION_28_29, MIGRATION_29_30, MIGRATION_31_32, MIGRATION_36_37, MIGRATION_37_38,
MIGRATION_38_39, MIGRATION_39_40, MIGRATION_40_41, MIGRATION_41_42, MIGRATION_42_43,
MIGRATION_43_44, MIGRATION_44_45, MIGRATION_45_46`.

Required upgrade effects (observable results only):

- **1→2**: rebuilds legacy integer-keyed `song`/`artist`/`playlist`/`playlist_song` into the string-id
  schema; assigns generated string artist/playlist ids; converts epoch-millis `create_date`/
  `modify_date` to UTC `LocalDateTime`; renumbers playlist positions densely per playlist starting at
  0; creates the map tables, indices, `download`, `search_history`, and initial views; preserves song
  `title/duration/liked/totalPlayTime` and memberships.
- **5→6**: copies `playlist.id` into `browseId` for ids not starting with `LP`.
- **6→7**: sets `song.inLibrary` from the legacy `createDate` for every song.
- **7→8**: drops song `createDate`/`modifyDate` columns.
- **9→10**: drops the legacy `download` table.
- **10→11**: drops song `downloadState` and artist `bannerUrl`/`description`/`createDate`.
- **11→12**: drops album `createDate`; sets `album.bookmarkedAt = lastUpdateTime`; recreates album
  rows from distinct `song.albumId/albumName`; creates `index_song_albumId`.
- **13→14**: sets playlist `createdAt`/`lastUpdateTime` to now.
- **16→17**: drops `isLocal`/`localPath` legacy columns; `playlist.bookmarkedAt = lastUpdateTime`;
  sets `isEditable = 1` where `browseId` is non-null.
- **18→19, 19→20**: replaces null `song.explicit` with 0.
- **20→21**: drops legacy `song.artistName`.
- **21→22 / 21→24 / 22→24 / 23→24**: add `libraryAddToken`, `libraryRemoveToken`, `romanizeLyrics`
  (default 1), `isDownloaded` (0), `isUploaded` (0) when absent.
- **24→25 / 37→38 / 38→39**: add `format.perceptualLoudnessDb` when absent.
- **27→28**: rebuilds `song`, `artist`, `album`, `playlist`; adds `lyricsOffset` (0), `isVideo` (0),
  the new artist/album/playlist columns; drops and recreates views.
- **28→29**: adds `playlist.isAutoSync` when absent.
- **29→30**: adds `song.isVideo`, `lyrics.provider` default `'Unknown'`.
- **31→32**: adds lyrics translation columns.
- **36→37**: no-op.
- **37→38**: creates `brain_activity_log`, `play_event`, legacy `taste_profile`; adds
  `format.perceptualLoudnessDb`.
- **39→40**: creates `beat_info`.
- **40→41**: adds `beat_info.mixInPointMs`/`mixOutPointMs`.
- **41→42**: paired spec deletes `brain_activity_log`, `play_event`, legacy `taste_profile`.
- **42→43**: rebuilds `playlist` adding `isPinned` (default false).
- **43→44**: adds `beat_info.keyPitchClass`/`keyIsMinor`.
- **44→45**: adds `isLocal` (default 0) to `song`/`artist`/`playlist` only when missing.
- **45→46**: adds `song.hideFromQuickPicks` (default 0) when missing.
- **46→47**: seeds `song_play_stats` for every `(lower(trim(title)) || '|' || trim(coalesce(artist,''))`
  key from `event` joined to `song`, summing `playTime`, counting plays, `skipCount = 0`,
  `lastPlayedAtMillis = max(event.timestamp)`; only rows with `playTime > 0`.
- **47→48**: rebuilds a legacy `taste_profile` (which had `genres`) into the `topArtistsJson` /
  `topTracksJson` / `topGenresJson` / `confidence` / `updatedAtMillis` shape; no-op when already new.

Migration behaviour: adding a column that is already present is a no-op (idempotent); individual
migration failures are swallowed/logged; and after any table rebuild the specified views exist again
with the SQL in §6.4. The internal mechanism for detecting an existing column is not normative.

### 6.6 Playback persistence models and file contract

`com.revenge.music.models.PersistQueue`:

```kotlin
data class PersistQueue(
  val title: String?,
  val items: List<MediaMetadata>,
  val mediaItemIndex: Int,
  val position: Long,
  val queueType: QueueType = QueueType.LIST,
  val queueData: QueueData? = null,
  val shuffledIndices: List<Int>? = null,
) : java.io.Serializable
```

`QueueType` is a `Serializable` sealed class with objects `LIST`, `YOUTUBE`, `YOUTUBE_ALBUM_RADIO`,
`LOCAL_ALBUM_RADIO`. `QueueData` is a `Serializable` sealed class with data classes
`YouTubeData(endpoint: String, continuation: String? = null)`,
`YouTubeAlbumRadioData(playlistId: String, albumSongCount: Int = 0, continuation: String? = null,
firstTimeLoaded: Boolean = false)`, and
`LocalAlbumRadioData(albumId: String, startIndex: Int = 0, playlistId: String? = null,
continuation: String? = null, firstTimeLoaded: Boolean = false)`.

`com.revenge.music.models.PersistPlayerState`:

```kotlin
data class PersistPlayerState(
  val playWhenReady: Boolean,
  val repeatMode: Int,
  val shuffleModeEnabled: Boolean,
  val volume: Float,
  val currentPosition: Long,
  val currentMediaItemIndex: Int,
  val playbackState: Int,
  val timestamp: Long = System.currentTimeMillis(),
) : java.io.Serializable
```

File contract (owned by `MusicService` in `:app`, must remain compatible):

- Queue snapshot file name `persistent_queue.data`; automix file `persistent_automix.data`; player
  state file `persistent_player_state.data`, all in `context.filesDir`.
- Serialisation is Java `ObjectOutputStream.writeObject`; deserialisation is `readObject() as
  PersistQueue` / `as PersistPlayerState`.
- A corrupt/unreadable file must be deleted and treated as absent, never crash startup.
- `PersistPlayerState.timestamp` defaults to wall-clock now at construction.
- Because `MediaMetadata` is `Serializable`, queue items and nested `QueueData`/`QueueType` must
  remain serialisable with stable class names. Do not add non-serialisable fields.

These types make playback state survive process death; see acceptance test D.

### 6.7 `com.revenge.music.utils` DataStore adapter

```kotlin
val Context.dataStore: DataStore<Preferences>              // name "settings"
operator fun <T> DataStore<Preferences>.get(key: Preferences.Key<T>): T?
fun <T> DataStore<Preferences>.get(key: Preferences.Key<T>, defaultValue: T): T
fun <T> preference(context: Context, key: Preferences.Key<T>, defaultValue: T): ReadOnlyProperty<Any?, T>
inline fun <reified T : Enum<T>> enumPreference(context: Context, key: Preferences.Key<String>, defaultValue: T): ReadOnlyProperty<Any?, T>
```

- Single preferences file named `"settings"` (file `settings.preferences_pb` under
  `files/datastore`, used by backup/restore).
- The blocking getters may block to read; they return `null`/default on any failure and never throw.
- `enumPreference` maps a stored string via case-insensitive `toEnum`; unknown/missing ⇒ default.
- Property delegates re-read on every access (no caching).

### 6.8 `com.revenge.music.utils.PlaybackLogManager`

```kotlin
enum class PlaybackLogLevel { INFO, WARNING, ERROR, DEBUG, BOT }
data class PlaybackLogEntry(
  val timestamp: String,        // "HH:mm:ss.SSS"
  val level: PlaybackLogLevel,
  val message: String,
  val details: String? = null,
)
object PlaybackLogManager {
  val logs: StateFlow<List<PlaybackLogEntry>>   // initial emptyList()
  fun log(level: PlaybackLogLevel, message: String, details: String? = null)
  fun clearLogs()
}
```

- At most 500 entries are retained; when exceeded, the oldest entries are dropped so the newest 500
  remain. The retention container is not normative.
- `logs` emits a new immutable list value on every `log`/`clearLogs`; `clearLogs` emits empty.
- Thread-safety: updates are safe from multiple producer threads; emissions on a `StateFlow`.
- Timestamp uses local time with milliseconds; no date component.

### 6.9 Cache qualifiers and small utilities

`com.revenge.music.di`:

```kotlin
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class PlayerCache
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DownloadCache
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope
```

`com.revenge.music.utils.NetworkConnectivityObserver(context: Context)`:

- Exposes `val networkStatus: Flow<Boolean>` where only the latest value is retained: slow collectors
  may miss intermediate changes.
- Monitors a `NetworkRequest` requiring `NET_CAPABILITY_INTERNET` and
  `NET_CAPABILITY_NOT_RESTRICTED`.
- Emits `true` on network available, `false` on network lost, and emits the initial
  `isCurrentlyConnected()` value to new collectors.
- If callback registration fails (restricted profile), emits `true`; registration failure is not
  thrown.
- `isCurrentlyConnected()` is `true` only when the active network has both `INTERNET` and
  `VALIDATED`; any exception ⇒ `false`.
- `unregister()` is idempotent and never throws.

`com.revenge.music.utils.CrashUtils`:

```kotlin
var exceptionReporter: ((Throwable) -> Unit)?
fun reportException(throwable: Throwable)
```

`reportException` logs through Timber (`Timber.e`) then invokes `exceptionReporter` when set. It never
throws because of a missing reporter.

### 6.10 `MediaMetadata` reproduced shape (needed by playback interfaces and persistence)

```kotlin
@Immutable
data class MediaMetadata(
  val id: String,
  val title: String,
  val artists: List<Artist>,
  val duration: Int,
  val thumbnailUrl: String? = null,
  val album: Album? = null,
  val setVideoId: String? = null,
  val musicVideoType: String? = null,
  val explicit: Boolean = false,
  val liked: Boolean = false,
  val likedDate: LocalDateTime? = null,
  val inLibrary: LocalDateTime? = null,
  val libraryAddToken: String? = null,
  val libraryRemoveToken: String? = null,
  val suggestedBy: String? = null,
  val source: QueueItemSource = QueueItemSource.USER,
) : java.io.Serializable {
  val isVideoSong: Boolean
  data class Artist(val id: String?, val name: String) : Serializable
  data class Album(val id: String, val title: String) : Serializable
}

enum class QueueItemSource { USER }
```

`isVideoSong` is `true` iff `musicVideoType != null` and not equal to the innertube constant
`MUSIC_VIDEO_TYPE_ATV` (`"MUSIC_VIDEO_TYPE_ATV"`). All nested classes are `Serializable`.

---

## 7. Downstream service persistence contracts (live in `:app`; not rewritten by this cluster)

These services consume the §6 contract. Their behavioural contracts must remain satisfiable; the
implementer of this cluster must not change the DAO/schema in ways that break them.

### 7.1 `com.revenge.music.playback.DownloadUtil`

- Exposes `database: MusicDatabase`, `downloads: MutableStateFlow<Map<String, Download>>`,
  `downloadManager`, `getDownload(songId): Flow<Download?>`, `release()`.
- On completion (`Download.STATE_COMPLETED`): records the download date as a UTC `LocalDateTime`
  derived from `download.updateTimeMs` when `> 0`, else the current time, and the title from
  `request.data` decoded as UTF-8 and trimmed, default `"Unknown"`. Inside
  `database.withTransaction` it ensures the song row is `isDownloaded = true` with that date: if no
  song row exists a minimal `SongEntity(id, title, dateDownload, isDownloaded = true)` is created;
  if the row already exists its title is left alone and only the downloaded info is updated
  (`updateDownloadedInfo(id, true, date)`).
- On `STATE_FAILED` / `STATE_REMOVING` and on `onDownloadRemoved`: calls
  `updateDownloadedInfo(id, false, null)`.
- On construction, loads the media3 download index into `downloads` and reconciles every
  `STATE_COMPLETED` entry into the DB exactly as above (download recovery after process death).
- Wi-fi-only preference toggles `Requirements.NETWORK_UNMETERED` vs `Requirements.NETWORK` without
  discarding progress.
- `companion computeDownloadState(songIds: Collection<String>?, downloads: Map<String, Download>):
  Int` precedence: empty/null ⇒ `STATE_STOPPED`; all completed ⇒ `STATE_COMPLETED`; any
  queued/downloading ⇒ `STATE_DOWNLOADING`; else `STATE_STOPPED`. `hasDownload` is true only for
  completed/queued/downloading.
- Max parallel downloads 3; `FormatEntity` and optional `LyricsEntity` are upserted from the player
  response; `SongEntity` is upserted with `isDownloaded/dateDownload` untouched by streaming.

### 7.2 `com.revenge.music.playback.ExportTaskQueue`

```kotlin
class ExportTaskQueue(
  private val scope: CoroutineScope,
  private val onIdle: () -> Unit,
  private val completionDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {
  val isIdle: Boolean
  fun submit(songId: String, export: suspend () -> Unit): Boolean
  fun cancelAll()
}
```

- Serial execution: exports run one at a time, in submission order.
- Deduplicate by `songId`: a second `submit` for a live id returns `false` and does not enqueue.
- `submit` returns `true` when accepted. `isIdle` is true when no tracked jobs remain.
- `onIdle` is invoked exactly once each time the tracked set transitions to empty, on the completion
  dispatcher, non-cancellable. A cancelled job still removes itself and may trigger idle.
- `cancelAll` cancels running and queued jobs; cancellation must not prevent `onIdle`.

### 7.3 `com.revenge.music.localmedia.LocalSongScanner` and `SupportedLocalAudio`

`LocalSongScanConfig` defaults and derived values (pure, testable):

```kotlin
enum class LocalFolderFilterMode { EXCLUDE, INCLUDE }

data class LocalSongScanConfig(
  val minimumDurationSeconds: Int = 0,
  val excludedFolders: Set<String> = emptySet(),
  val includedFolders: Set<String> = emptySet(),
  val filterMode: LocalFolderFilterMode = EXCLUDE,
  val allowClearAll: Boolean = false,
) {
  val sanitizedMinimumDurationSeconds: Int      // coerceAtLeast(0)
  val sanitizedExcludedFolders: Set<String>     // normalise + case-insensitive dedupe
  val sanitizedIncludedFolders: Set<String>
  val hasActiveFolderFilter: Boolean
  companion object {
    fun normalizeFolderEntry(raw: String): String
    fun deduplicateFolderEntries(entries: Iterable<String>): Set<String>
  }
}
```

- `normalizeFolderEntry`: trim; backslashes → `/`; collapse repeated `/`; trim leading/trailing `/`.
- `deduplicateFolderEntries`: normalise; drop empties; keep first spelling per lowercase key
  (`Locale.ROOT`); iteration order preserved.
- `scanDevice(scanConfig = LocalSongScanConfig()): LocalSongScanSummary` runs off the caller's
  thread and applies its database changes inside one `database.withTransaction` (a scan is atomic).
- `LocalSongScanSummary(scannedSongs: Int, removedSongs: Int)`.
- Scans `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` selecting `SIZE > 0`, duration `>= min*1000`
  when min > 0 else `> 0`, `IS_PENDING = 0` (API 29+), `is_trashed = 0` (API 30+); order by title
  NOCASE then `_ID`.
- Folder matching: a track matches a configured folder when the normalised lower-case track folder
  equals it, starts with `folder/`, ends with `/folder`, or contains `/folder/`. In INCLUDE mode
  unmatched tracks are skipped; in EXCLUDE mode matched tracks are skipped.
- Empty-scan safeguard: when the query yields zero tracks **and** the query completed **and** the
  device had rows **and** an active folder filter matched zero rows, clearing is allowed. Clearing is
  also allowed when `allowClearAll` or when no local songs currently exist. Otherwise the scan is a
  no-op with summary `(0, 0)` — it must not wipe local songs/playlists/history because of a transient
  empty MediaStore.
- Pruning: tracks no longer present are deleted (the write may be split into bounded batches to stay
  within SQLite limits; batch size is not normative); local albums/artists, formats and play counts
  are pruned afterwards. Existing user state (`liked`, `likedDate`, `totalPlayTime`,
  bookmarked flags, explicit, year, download date) is preserved for re-scanned tracks.
- ID schemes: local song id = MediaStore content URI string; album id = `LOCAL_ALBUM_<mediaStoreId>`
  when the MediaStore album id is `> 0`, else `LOCAL_ALBUM_<uuid-without-dashes of "name|primaryArtistId">`;
  artist id = `LOCAL_ARTIST_<mediaStoreArtistId>` for a single artist when the id is `> 0`, else
  `LOCAL_ARTIST_<uuid hash of "name|index">`.
- Artist names split on `[,;/&]`, trimmed, blanks dropped, fallback to the raw artist.
- Missing title falls back to display-name stem, then a localised "unknown"; `<unknown>` artist/album
  sentinels are treated as missing.
- `SupportedLocalAudio.isSupported(displayName, mimeType)`: blocked extensions
  `ape,cue,dsd,dsf,dff,midi,iso,tta,wv,wvc` always return false; known extensions (`aac,amr,flac,m4a,
  m4b,m4p,mid,mka,mp3,mp4,oga,ogg,opus,wav,weba,webm,3ga,3gp`) return true; otherwise accept when the
  MIME type (before `;`, trimmed, lower-case) is in the supported set. `isSupportedMimeType` also
  accepts `"audio/*"`.

### 7.4 Backup/restore (`BackupRestoreViewModel`)

- `SETTINGS_FILENAME = "settings.preferences_pb"`.
- Backup writes a zip containing `settings.preferences_pb` (from `files/datastore/`) and the SQLite
  DB file named `song.db`; it calls `database.checkpoint()` before reading the DB file.
- Restore: reads zip entries; copies settings; for `song.db`, copies to a temp file, reads the backup
  schema version from the SQLite header (THE `user_version` field at byte offset 60, big-endian int),
  and rejects when `backupVersion > currentVersion` with no changes applied. Otherwise closes the
  database, deletes `-wal` and `-shm`, overwrites the DB file, deletes the persisted queue file, stops
  the music service and restarts the app. Any failure must leave the user able to retry and must not
  corrupt the current DB (temp-file strategy).
- CSV import: parses the first up-to-6 rows for preview; header detection is "first line contains a
  comma"; column mapping includes artist, title, optional URL; quoted CSV fields are supported;
  progress callbacks at every 50 lines and on the last line; empty result produces a user-visible
  failure. M3U import parses `#EXTM3U` / `#EXTINF:` lines.
- These paths depend only on the §6 DAO/schema contract and `database.checkpoint()/openHelper`.

---

## 8. Target files and responsibilities

All paths are under the `:core` module source tree. The implementer must create/replace each file;
public FQCNs and signatures above are hard compatibility constraints.

| # | Target path | Responsibility |
|---|---|---|
| 1 | `core/src/main/kotlin/com/revenge/core/repository/SongRepositoryImpl.kt` | `@Singleton`/`@Inject` adapter implementing `SongRepository` over `MusicDatabase`; five methods per §3.1. |
| 2 | `core/src/main/kotlin/com/revenge/core/repository/PlaylistRepositoryImpl.kt` | `@Singleton`/`@Inject` adapter implementing `PlaylistRepository`; four methods per §3.2. |
| 3 | `core/src/main/kotlin/com/revenge/core/mapping/DomainMappers.kt` | Four `toDomain` overloads per §4, including local thumbnail derivation. |
| 4 | `core/src/main/kotlin/com/revenge/music/playback/CastDeviceKind.kt` | Enum plus `fromName(name, description)` classification per §5.1. |
| 5 | `core/src/main/kotlin/com/revenge/music/playback/ILyricsHelper.kt` | `LyricsWithProvider` data class and `ILyricsHelper` interface per §5.2. |
| 6 | `core/src/main/kotlin/com/revenge/music/playback/ISyncUtils.kt` | `likeSong(SongEntity)` fire-and-forget interface per §5.3. |
| 7 | `core/src/main/kotlin/com/revenge/music/playback/IWidgetManager.kt` | `updateWidgets(...)` suspend interface with defaults per §5.4. |
| 8 | `core/src/main/kotlin/com/revenge/music/playback/IYTPlayerUtils.kt` | `getStreamInfo`/`getAudioConfig` suspend interface per §5.5. |
| 9 | `core/src/main/kotlin/com/revenge/music/models/PersistQueue.kt` | Serializable queue snapshot and `QueueType`/`QueueData` hierarchy per §6.6. |
| 10 | `core/src/main/kotlin/com/revenge/music/models/PersistPlayerState.kt` | Serializable transport-state snapshot per §6.6. |
| 11 | `core/src/main/kotlin/com/revenge/music/utils/DataStore.kt` | `Context.dataStore` accessor, blocking getters, preference/enum delegates per §6.7. |
| 12 | `core/src/main/kotlin/com/revenge/music/utils/PlaybackLogManager.kt` | Level/entry types and the bounded 500-entry StateFlow log per §6.8. |
| 13 | `core/src/main/kotlin/com/revenge/music/di/Qualifiers.kt` | `PlayerCache`, `DownloadCache`, `ApplicationScope` qualifiers per §6.9. |
| 14 | `core/src/main/kotlin/com/revenge/music/utils/NetworkConnectivityObserver.kt` | Latest-value connectivity flow and registration lifecycle per §6.9. |
| 15 | `core/src/main/kotlin/com/revenge/music/utils/CrashUtils.kt` | `exceptionReporter` hook and `reportException` per §6.9. |

Required dependency files in the same module (contract §6.1–§6.5; may be co-owned with a database
cluster, but this cluster's acceptance tests exercise them):

- `core/src/main/kotlin/com/revenge/music/db/MusicDatabase.kt`
- `core/src/main/kotlin/com/revenge/music/db/DatabaseDao.kt`
- `core/src/main/kotlin/com/revenge/music/db/Converters.kt`
- `core/src/main/kotlin/com/revenge/music/db/entities/{LocalItem,SongEntity,Song,ArtistEntity,AlbumEntity,Album,PlaylistEntity,Playlist,PlaylistSongMap,PlaylistSong,PlaylistSongMapPreview,SortedSongArtistMap,SortedSongAlbumMap,SongArtistMap,SongAlbumMap,AlbumArtistMap,Event,EventWithSong,FormatEntity,LyricsEntity,SearchHistory,RelatedSongMap,SetVideoIdEntity,PlayCountEntity,RecognitionHistory,SpeedDialItem,BeatInfoEntity,SongPlayStatsEntity,RecommendationExclusionEntity,TasteProfileEntity}.kt`
- `core/src/main/kotlin/com/revenge/music/models/MediaMetadata.kt` and
  `MediaMetadataMappers.kt` (relation/entity ↔ `MediaMetadata` converters used by scanner/download).
- `core/schemas/com.revenge.music.db.InternalDatabase/48.json` (and prior schema files) must remain
  committed and unchanged unless the DB version is intentionally bumped.

---

## 9. Threading, error and edge-case summary

- Repository write methods (`updateLiked`, `incrementPlayTime`, `addSongToPlaylist`,
  `removeSongFromPlaylist`) enqueue work and return before completion. They never block the caller
  and never rethrow DB exceptions to the caller.
- Repository read methods expose Room flows; collection happens on the caller's coroutine context;
  Room performs the query on its own executor. DB exceptions surface at collection.
- `withTransaction` is the only completion/atomicity guarantee; download and scanner code rely on it.
- Lenient map inserts swallow constraint violations; `insert(song)` reports `-1` on duplicate.
- Duplicate songs in a playlist are allowed; positions are not renumbered on removal; concurrent
  adds may produce equal positions.
- `getRecentlyPlayed` de-duplicates by id, most-recent wins, then truncates to `limit`.
- `SongEntity.toDomain` deliberately yields an empty artist list; relation mapper yields artists.
- Local thumbnail derivation is the only computation in mapping and is deterministic.
- Cast classification is deterministic and never throws.
- Connectivity observer emits the latest value only; registration failure degrades to "connected".
- Playback log retention is bounded at 500 and never blocks.
- All timestamps in the DB are UTC epoch millis; `LocalDateTime.now()` values for `likedDate` are
  written through the same UTC converter.

---

## 10. Acceptance tests

Tests are deterministic and must not require a network, device sensors, real MediaStore, or real
audio hardware. Use in-memory Room (`Room.inMemoryDatabaseBuilder`) with the real entity/schema,
Robolectric only where Android classes are needed, and fakes for service collaborators.

### A. Database schema and migration

A1. **Fresh schema parity.** Open a fresh database; assert `version == 48`, every table/column/index/
    foreign key in §6.4 exists, and the three views exist with the specified SQL. Compare against
    `48.json` where practical.
A2. **Converters.** UTC epoch round-trip preserves `2024-02-29T13:45:07.123`; `null ↔ null`.
A3. **Upgrade from 47 to 48** with a legacy `taste_profile(id, genres, confidence, patternsFound,
    modelVersion, updatedAt)`: after migration the table has the new columns, the legacy table is
    replaced, and no crash occurs. Running the migration on an already-new table is a no-op.
A4. **46→47 stats seeding.** Two `event` rows with `playTime > 0` for one song and one `playTime = 0`
    row: after migration exactly one `song_play_stats` row exists with `playCount = 2`,
    `totalPlayTimeMs` = sum of positive play times, `skipCount = 0`.
A5. **27→28 rebuild preserves rows.** Create legacy song/artist/album/playlist rows; migrate; assert
    ids, titles, liked, totalPlayTime and memberships survive; `lyricsOffset = 0`, `isVideo = 0`.
A6. **44→45 idempotence.** A DB already containing `isLocal` migrates without duplicate-column error.
A7. **Foreign-key cascade.** Delete a song; its `song_artist_map`, `song_album_map`,
    `playlist_song_map`, `event`, `related_song_map` rows are removed.
A8. **Duplicate playlist membership allowed.** Insert two `playlist_song_map` rows with the same
    `(playlistId, songId)` and different positions; both persist.

### B. Repository behaviour

B1. **getSong null.** Unknown id emits `null`; after inserting the song the same flow emits it.
B2. **getLikedSongs ordering.** Insert liked songs A, B, C (in that order) and one unliked; result is
    `[A, B, C]`. Updating A to unliked removes it on the next emission.
B3. **getRecentlyPlayed dedupe.** Events newest→oldest: `[s1, s2, s1, s3]`; limit 10 ⇒
    `[s1, s2, s3]` with s1's most-recent event position preserved. limit 2 ⇒ `[s1, s2]`.
B4. **getRecentlyPlayed non-positive.** limit 0 and limit −5 ⇒ empty list.
B5. **updateLiked true/false.** After `updateLiked(id, true)`, `liked == true` and `likedDate != null`;
    after `updateLiked(id, false)`, `liked == false` and `likedDate == null` (await via
    `withTransaction`/flow first).
B6. **incrementPlayTime.** Starting at 0, two calls of 1_000 and 2_500 ⇒ 3_500; negative input
    subtracts; unknown id is a no-op.
B7. **getAllPlaylists filter.** Playlists: bookmarked editable, bookmarked non-editable, unbookmarked.
    Result contains both bookmarked playlists ordered by name; unbookmarked excluded.
B8. **getAllPlaylists count and thumbnail.** Playlist with three songs (positions 0–2) ⇒
    `songCount == 3`; thumbnail is the first non-null song thumbnail. Explicit
    `playlist.thumbnailUrl` overrides song thumbnails.
B9. **getPlaylistSongs ordering and duplicates.** Maps at positions 2, 0, 1 ⇒ songs in position order;
    a duplicated membership yields the song twice. Unknown playlist ⇒ empty.
B10. **addSongToPlaylist positions.** Empty playlist ⇒ inserted position 0; existing max 4 ⇒
     inserted position 5. Unknown playlist/song ⇒ row not created and no exception escapes the
     adapter.
B11. **removeSongFromPlaylist.** Removes all duplicate memberships for that playlist, leaves other
     playlists' memberships intact, and does not renumber remaining positions.

### C. Mapping

C1. **Relation mapper artists/album.** Two artists in relation order plus an album ⇒ domain
    `artists` in the same order, `album` from the relation, `durationSeconds` copied (including −1).
C2. **Flat mapper artists empty.** `SongEntity.toDomain()` always has `artists == emptyList()`.
C3. **Local thumbnail via album id.** `isLocal = true`, `thumbnailUrl = null`,
    `albumId = "LOCAL_ALBUM_42"` ⇒ `content://media/external/audio/albumart/42`. `LOCAL_ALBUM_0`,
    missing/non-numeric ⇒ song `id`. Non-local with null thumbnail ⇒ `null`.
C4. **Remote thumbnail precedence.** Non-null `thumbnailUrl` is used verbatim even for local songs.
C5. **Album fallback.** Relation album absent, `albumId = "al"`, `albumName = null` ⇒
    `AlbumRef("al", "")`.
C6. **Playlist mapping.** `PlaylistEntity.toDomain()` defaults `songCount = 0`; explicit value
    overrides. Relation mapping uses explicit thumbnail first, else first preview song thumbnail.

### D. Playback state survival

D1. **Queue round-trip.** Serialise a `PersistQueue` containing two `MediaMetadata` items (with nested
    artists, album, `QueueData.YouTubeAlbumRadioData`) and `shuffledIndices`; deserialise; assert
    structural equality of every field.
D2. **Player state round-trip.** `PersistPlayerState` with all fields set round-trips; default
    `timestamp` is within a reasonable window of construction time when omitted.
D3. **Corrupt file.** Reading garbage bytes with the production read helper is treated as absent and
    the file is deleted; startup continues.
D4. **Queue type/data polymorphism.** Each `QueueType` object and each `QueueData` subtype survives a
    serialise/deserialise cycle with `is` identity.

### E. Cast routing fallback

E1. `fromName("Living Room TV", null) == TV`.
E2. `fromName("Kitchen Speaker", null) == SPEAKER`; `fromName("Nest Mini", null) == SPEAKER`.
E3. `fromName("Bedroom", "Chromecast Ultra") == CHROMECAST`.
E4. `fromName("", "") == UNKNOWN`; `fromName("Random", null) == UNKNOWN`.
E5. Precedence: `fromName("TV speaker", "chromecast") == TV`.
E6. Case-insensitivity: `fromName("SMART TV", "MODEL") == TV`.

### F. Download recovery and state

F1. `computeDownloadState(empty, …) == STOPPED`; `computeDownloadState(null, …) == STOPPED`.
F2. All completed ⇒ COMPLETED; one downloading + rest completed ⇒ DOWNLOADING; only stopped/failed ⇒
    STOPPED; any queued ⇒ DOWNLOADING.
F3. `hasDownload` true for completed/queued/downloading; false for stopped/failed/missing.
F4. **Recovery reconciliation.** Seed a fake download index with one completed download; run the init
    reconciliation against an in-memory DB containing no song; assert a `SongEntity` row exists with
    `isDownloaded = true`, non-null `dateDownload`, and the decoded title (or `"Unknown"`).
F5. **Recovery update path.** DB already contains the song; reconciliation keeps `title` but sets
    `isDownloaded = true` and a non-null `dateDownload` via `updateDownloadedInfo`.
F6. **Failure/removal sync.** A failed or removed download sets `isDownloaded = false` and
    `dateDownload = null`.
F7. **Timestamp source.** A download with `updateTimeMs > 0` uses that instant; with 0 uses current
    time; both are interpreted as UTC.

### G. Export queue

G1. Serial execution: submit A (suspends) then B; B does not run until A completes; `onIdle` fires
    once after both.
G2. Deduplication: second `submit("same")` returns false; export runs once.
G3. `cancelAll` stops running and queued exports; `onIdle` still fires once and `isIdle` becomes true.
G4. Cancellation before start ⇒ zero exports run, `onIdle` once.
G5. Completing one of several exports never invokes `onIdle` early.

### H. Local scanner

H1. `normalizeFolderEntry` cases: `" Music\\Rock// "` ⇒ `"Music/Rock"`; `"/a/b/"` ⇒ `"a/b"`.
H2. `deduplicateFolderEntries(["Music/Rock", "music/rock", "  MUSIC/ROCK/ "])` ⇒ one entry, first
    spelling `"Music/Rock"`.
H3. `sanitizedMinimumDurationSeconds` clamps negatives to 0.
H4. `hasActiveFolderFilter`: true for non-empty include set in INCLUDE mode; true for non-empty exclude
    set in EXCLUDE mode; false for empty active set.
H5. Folder matching: `"Music/Rock"` matches config `"music/rock"`, `"rock"`, and prefix/suffix cases;
    unrelated `"Podcasts"` does not.
H6. Empty-scan safeguard: device rows > 0, active folder filter, zero matches, `allowClearAll = false`,
    existing local songs present ⇒ summary `(0, 0)` and local songs remain.
H7. Empty-scan clear: zero tracks with `allowClearAll = true` ⇒ local songs cleared. Zero tracks and
    no existing local songs ⇒ clear is a no-op (no error).
H8. Rescan preservation: a re-scanned track keeps `liked`, `likedDate`, `totalPlayTime`, `year`,
    `explicit`, `dateDownload` from the existing row.
H9. ID scheme: single artist with MediaStore id 7 ⇒ `LOCAL_ARTIST_7`; album with MediaStore id 9 ⇒
    `LOCAL_ALBUM_9`; missing ids produce stable `LOCAL_ARTIST_`/`LOCAL_ALBUM_` hashes (same input ⇒
    same id).
H10. `SupportedLocalAudio`: `"a.mp3"` supported; `"a.ape"` unsupported; unknown extension with MIME
     `audio/flac` supported; `audio/*` accepted only by `isSupportedMimeType`.

### I. Backup/restore

I1. Backup archive contains exactly the settings entry and `song.db` entry; `checkpoint()` is called
    before the DB file is read.
I2. Restore from a backup whose header `user_version` is greater than the current DB version is
    rejected without modifying the live DB; the temp file is cleaned up.
I3. Restore from an equal/older version replaces the DB, deletes `-wal`/`-shm`, deletes the persisted
    queue file, and restarts.
I4. Corrupt archive / missing entries ⇒ user-visible failure and live DB unchanged.
I5. CSV preview: first six rows parsed, quoted commas preserved, header detected when the first line
    contains a comma.
I6. CSV import: mapping by column indices; rows missing title or artist skipped; progress fires at
    every 50 lines and at completion; result empty ⇒ failure feedback.

### J. Playback log and connectivity utilities

J1. `PlaybackLogManager.log` 501 times ⇒ `logs.value.size == 500`; the oldest entry is dropped and the
    newest retained; `clearLogs()` ⇒ empty.
J2. Entries preserve provided level/message/details and produce a `HH:mm:ss.SSS` timestamp.
J3. `NetworkConnectivityObserver.networkStatus` is conflated: rapid true/false emissions deliver only
    the latest to a slow collector; initial value is emitted.
J4. `reportException` invokes a registered `exceptionReporter` exactly once and does not throw when
    unset.

### K. Not applicable to this cluster

- **Audio focus:** implemented by the media session in `:app`; no audio-focus API exists in `:core`.
  This specification must not introduce one.
- **EQ parsing:** no equaliser parser exists in `:core`; belongs to `:dsp-core`/`:audio-dsp`.
- **USB routing fallback:** implemented by `playback/AudioSinkSelector` in `:playback`; this cluster
  only supplies `CastDeviceKind.UNKNOWN` as the generic device fallback (test E4).

---

## 11. Non-goals and compatibility notes

- Do not add caching, retry, offline logic, or new public API to the repository adapters.
- Do not rename or reorder any constructor/method parameter; downstream uses named arguments and
  Hilt/DI matches exact signatures.
- Do not change `song.db`, the database version, entity columns, indices, foreign keys, view SQL,
  migration ids, or the identity hash without an intentional schema bump; backups and upgrades depend
  on them.
- The lenient/silent error behaviour of write helpers is intentional and observable; do not convert
  it to throwing behaviour.
- Download/export/scanner/backup classes stay in `:app`; only their persistence dependencies are in
  scope here.
- Additional private helpers are allowed; additional public types must not change §3–§7.
