package com.revenge.music.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import com.revenge.innertube.pages.AlbumPage
import com.revenge.innertube.pages.ArtistPage
import com.revenge.innertube.models.PlaylistItem
import com.revenge.music.constants.AlbumSortType
import com.revenge.music.constants.ArtistSongSortType
import com.revenge.music.constants.ArtistSortType
import com.revenge.music.constants.PlaylistSortType
import com.revenge.music.constants.SongSortType
import com.revenge.music.db.entities.Album
import com.revenge.music.db.entities.AlbumArtistMap
import com.revenge.music.db.entities.AlbumEntity
import com.revenge.music.db.entities.AlbumWithSongs
import com.revenge.music.db.entities.Artist
import com.revenge.music.db.entities.ArtistEntity
import com.revenge.music.db.entities.BeatInfoEntity
import com.revenge.music.db.entities.Event
import com.revenge.music.db.entities.EventWithSong
import com.revenge.music.db.entities.FormatEntity
import com.revenge.music.db.entities.LyricsEntity
import com.revenge.music.db.entities.PlayCountEntity
import com.revenge.music.db.entities.Playlist
import com.revenge.music.db.entities.PlaylistEntity
import com.revenge.music.db.entities.PlaylistSong
import com.revenge.music.db.entities.PlaylistSongMap
import com.revenge.music.db.entities.RecognitionHistory
import com.revenge.music.db.entities.RelatedSongMap
import com.revenge.music.db.entities.SearchHistory
import com.revenge.music.db.entities.SetVideoIdEntity
import com.revenge.music.db.entities.Song
import com.revenge.music.db.entities.SongAlbumMap
import com.revenge.music.db.entities.SongArtistMap
import com.revenge.music.db.entities.SongEntity
import com.revenge.music.db.entities.SongWithStats
import com.revenge.music.models.MediaMetadata
import com.revenge.music.models.toMediaMetadata
import com.revenge.music.models.toSongEntity
import com.revenge.music.ui.utils.resize
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
interface DatabaseDao {

    // ------------------------------------------------------------------
    // Sort helpers
    // ------------------------------------------------------------------

    fun songs(sortType: SongSortType, descending: Boolean): Flow<List<Song>> {
        val base = when (sortType) {
            SongSortType.CREATE_DATE -> songsByRowIdAsc()
            SongSortType.NAME -> songsByNameAsc()
            SongSortType.ARTIST -> songsByArtistAsc()
            SongSortType.PLAY_TIME -> songsByPlayTimeAsc()
        }
        return base.orderFlowed(descending)
    }

    fun likedSongs(sortType: SongSortType, descending: Boolean): Flow<List<Song>> {
        val base = when (sortType) {
            SongSortType.CREATE_DATE -> likedSongsByRowIdAsc()
            SongSortType.NAME -> likedSongsByNameAsc()
            SongSortType.ARTIST -> likedSongsByNameAsc()
            SongSortType.PLAY_TIME -> likedSongsByPlayTimeAsc()
        }
        return base.orderFlowed(descending)
    }

    fun artistSongs(
        artistId: String,
        sortType: ArtistSongSortType,
        descending: Boolean,
        fromTimeStamp: Long? = null,
        toTimeStamp: Long? = null,
        limit: Int = -1,
    ): Flow<List<Song>> {
        val base = when (sortType) {
            ArtistSongSortType.CREATE_DATE -> artistSongsByCreateDateAsc(artistId)
            ArtistSongSortType.NAME -> artistSongsByNameAsc(artistId)
            ArtistSongSortType.PLAY_TIME ->
                if (fromTimeStamp != null && toTimeStamp != null) {
                    mostPlayedSongsByArtist(artistId, fromTimeStamp, toTimeStamp)
                } else {
                    artistSongsByPlayTimeAsc(artistId)
                }
        }
        return base.map { list ->
            val cut = if (limit > 0) list.take(limit) else list
            if (descending) cut.reversed() else cut
        }
    }

    fun downloadedSongs(sortType: SongSortType, descending: Boolean): Flow<List<Song>> {
        val base = when (sortType) {
            SongSortType.CREATE_DATE, SongSortType.ARTIST -> downloadedSongsByCreateDateAsc()
            SongSortType.NAME -> downloadedSongsByNameAsc()
            SongSortType.PLAY_TIME -> downloadedSongsByPlayTimeAsc()
        }
        return base.orderFlowed(descending)
    }

    fun uploadedSongs(sortType: SongSortType, descending: Boolean): Flow<List<Song>> {
        val base = when (sortType) {
            SongSortType.CREATE_DATE, SongSortType.ARTIST -> uploadedSongsByCreateDateAsc()
            SongSortType.NAME -> uploadedSongsByNameAsc()
            SongSortType.PLAY_TIME -> uploadedSongsByPlayTimeAsc()
        }
        return base.orderFlowed(descending)
    }

    fun artists(sortType: ArtistSortType, descending: Boolean): Flow<List<Artist>> {
        val base = when (sortType) {
            ArtistSortType.CREATE_DATE -> artistsByCreateDateAsc()
            ArtistSortType.NAME -> artistsByNameAsc()
            ArtistSortType.SONG_COUNT -> artistsBySongCountAsc()
            ArtistSortType.PLAY_TIME -> artistsByPlayTimeAsc()
        }
        return base.orderFlowed(descending).map { list ->
            list.filter { it.artist.isYouTubeArtist || it.artist.isLocal }
        }
    }

    fun artistsBookmarked(sortType: ArtistSortType, descending: Boolean): Flow<List<Artist>> {
        val base = when (sortType) {
            ArtistSortType.CREATE_DATE -> artistsBookmarkedByCreateDateAsc()
            ArtistSortType.NAME -> artistsBookmarkedByNameAsc()
            ArtistSortType.SONG_COUNT -> artistsBookmarkedBySongCountAsc()
            ArtistSortType.PLAY_TIME -> artistsBookmarkedByPlayTimeAsc()
        }
        return base.orderFlowed(descending).map { list ->
            list.filter { it.artist.isYouTubeArtist || it.artist.isLocal }
        }
    }

    fun albums(sortType: AlbumSortType, descending: Boolean): Flow<List<Album>> =
        albumsAsc(sortType).orderFlowed(descending)

    fun albumsLiked(sortType: AlbumSortType, descending: Boolean): Flow<List<Album>> =
        albumsLikedAsc(sortType).orderFlowed(descending)

    fun albumsUploaded(sortType: AlbumSortType, descending: Boolean): Flow<List<Album>> =
        albumsUploadedAsc(sortType).orderFlowed(descending)

    private fun albumsAsc(sortType: AlbumSortType): Flow<List<Album>> = when (sortType) {
        AlbumSortType.CREATE_DATE -> albumsByCreateDateAsc()
        AlbumSortType.NAME -> albumsByNameAsc()
        AlbumSortType.ARTIST -> albumsByArtistAsc()
        AlbumSortType.YEAR -> albumsByYearAsc()
        AlbumSortType.SONG_COUNT -> albumsBySongCountAsc()
        AlbumSortType.LENGTH -> albumsByLengthAsc()
        AlbumSortType.PLAY_TIME -> albumsByPlayTimeAsc()
    }

    private fun albumsLikedAsc(sortType: AlbumSortType): Flow<List<Album>> = when (sortType) {
        AlbumSortType.CREATE_DATE -> albumsLikedByCreateDateAsc()
        AlbumSortType.NAME -> albumsLikedByNameAsc()
        AlbumSortType.ARTIST -> albumsLikedByArtistAsc()
        AlbumSortType.YEAR -> albumsLikedByYearAsc()
        AlbumSortType.SONG_COUNT -> albumsLikedBySongCountAsc()
        AlbumSortType.LENGTH -> albumsLikedByLengthAsc()
        AlbumSortType.PLAY_TIME -> albumsLikedByPlayTimeAsc()
    }

    private fun albumsUploadedAsc(sortType: AlbumSortType): Flow<List<Album>> = when (sortType) {
        AlbumSortType.CREATE_DATE -> albumsUploadedByCreateDateAsc()
        AlbumSortType.NAME -> albumsUploadedByNameAsc()
        AlbumSortType.ARTIST -> albumsUploadedByArtistAsc()
        AlbumSortType.YEAR -> albumsUploadedByYearAsc()
        AlbumSortType.SONG_COUNT -> albumsUploadedBySongCountAsc()
        AlbumSortType.LENGTH -> albumsUploadedByLengthAsc()
        AlbumSortType.PLAY_TIME -> albumsUploadedByPlayTimeAsc()
    }

    fun playlists(sortType: PlaylistSortType, descending: Boolean): Flow<List<Playlist>> {
        val base = when (sortType) {
            PlaylistSortType.CREATE_DATE -> playlistsByCreateDateAsc()
            PlaylistSortType.NAME -> playlistsByNameAsc()
            PlaylistSortType.SONG_COUNT -> playlistsBySongCountAsc()
            PlaylistSortType.LAST_UPDATED -> playlistsByLastUpdatedAsc()
        }
        return base.orderFlowed(descending).map { list ->
            val pinned = list.filter { it.playlist.isPinned }
            val rest = list.filterNot { it.playlist.isPinned }
            pinned + rest
        }
    }

    private fun <T> Flow<List<T>>.orderFlowed(descending: Boolean): Flow<List<T>> =
        if (descending) map { it.asReversed() } else this

    // ------------------------------------------------------------------
    // Library songs
    // ------------------------------------------------------------------

    @Transaction
    @Query("SELECT * FROM song WHERE inLibrary IS NOT NULL ORDER BY rowid ASC")
    fun songsByRowIdAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE inLibrary IS NOT NULL ORDER BY inLibrary ASC")
    fun songsByCreateDateAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE inLibrary IS NOT NULL ORDER BY title COLLATE NOCASE ASC, id ASC")
    fun songsByNameAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE inLibrary IS NOT NULL ORDER BY totalPlayTime ASC")
    fun songsByPlayTimeAsc(): Flow<List<Song>>

    @Transaction
    @Query(
        "SELECT * FROM song WHERE inLibrary IS NOT NULL ORDER BY albumName ASC, " +
            "(SELECT GROUP_CONCAT(name, ', ') FROM (" +
            "SELECT artist.name AS name FROM song_artist_map " +
            "JOIN artist ON artist.id = song_artist_map.artistId " +
            "WHERE song_artist_map.songId = song.id ORDER BY song_artist_map.position)) ASC, " +
            "title COLLATE NOCASE ASC",
    )
    fun songsByArtistAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song ORDER BY totalPlayTime DESC LIMIT :limit")
    fun topSongs(limit: Int): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE liked = 1 ORDER BY rowid ASC")
    fun likedSongsByRowIdAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE liked = 1 ORDER BY likedDate ASC, rowid ASC")
    fun likedSongsByCreateDateAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE liked = 1 ORDER BY title COLLATE NOCASE ASC, id ASC")
    fun likedSongsByNameAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE liked = 1 ORDER BY totalPlayTime ASC")
    fun likedSongsByPlayTimeAsc(): Flow<List<Song>>

    @Query("SELECT COUNT(*) FROM song WHERE liked = 1")
    fun likedSongsCount(): Flow<Int>

    @Transaction
    @Query(
        "SELECT song.* FROM song JOIN song_album_map ON song_album_map.songId = song.id " +
            "WHERE song_album_map.albumId = :albumId",
    )
    fun albumSongs(albumId: String): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM playlist_song_map WHERE playlistId = :playlistId ORDER BY position ASC")
    fun playlistSongs(playlistId: String): Flow<List<PlaylistSong>>

    @Transaction
    @Query(
        "SELECT song.* FROM song JOIN song_artist_map ON song_artist_map.songId = song.id " +
            "WHERE song_artist_map.artistId = :artistId AND song.inLibrary IS NOT NULL " +
            "ORDER BY song.inLibrary ASC",
    )
    fun artistSongsByCreateDateAsc(artistId: String): Flow<List<Song>>

    @Transaction
    @Query(
        "SELECT song.* FROM song JOIN song_artist_map ON song_artist_map.songId = song.id " +
            "WHERE song_artist_map.artistId = :artistId AND song.inLibrary IS NOT NULL " +
            "ORDER BY song.title COLLATE NOCASE ASC, song.id ASC",
    )
    fun artistSongsByNameAsc(artistId: String): Flow<List<Song>>

    @Transaction
    @Query(
        "SELECT song.* FROM song JOIN song_artist_map ON song_artist_map.songId = song.id " +
            "WHERE song_artist_map.artistId = :artistId AND song.inLibrary IS NOT NULL " +
            "ORDER BY song.totalPlayTime ASC",
    )
    fun artistSongsByPlayTimeAsc(artistId: String): Flow<List<Song>>

    @Transaction
    @Query(
        "SELECT song.* FROM event JOIN song ON song.id = event.songId " +
            "JOIN song_artist_map ON song_artist_map.songId = song.id " +
            "WHERE song_artist_map.artistId = :artistId " +
            "AND event.timestamp > :fromTimeStamp AND event.timestamp <= :toTimeStamp " +
            "GROUP BY song.id ORDER BY SUM(event.playTime) DESC",
    )
    fun mostPlayedSongsByArtist(
        artistId: String,
        fromTimeStamp: Long,
        toTimeStamp: Long,
    ): Flow<List<Song>>

    @Transaction
    @Query(
        "SELECT song.* FROM song JOIN song_artist_map ON song_artist_map.songId = song.id " +
            "WHERE song_artist_map.artistId = :artistId AND song.inLibrary IS NOT NULL " +
            "LIMIT :previewSize",
    )
    fun artistSongsPreview(artistId: String, previewSize: Int = 3): Flow<List<Song>>

    // ------------------------------------------------------------------
    // Recommendations
    // ------------------------------------------------------------------

    @Transaction
    @Query(
        "SELECT song.* FROM song WHERE song.hideFromQuickPicks = 0 AND song.id IN (" +
            "SELECT id FROM (SELECT song.id AS id FROM song JOIN event ON event.songId = song.id " +
            "ORDER BY event.id DESC LIMIT 5) " +
            "UNION SELECT id FROM (SELECT song.id AS id FROM song JOIN event ON event.songId = song.id " +
            "WHERE event.timestamp >= :now - 604800000 GROUP BY song.id " +
            "ORDER BY SUM(event.playTime) DESC LIMIT 5) " +
            "UNION SELECT id FROM (SELECT song.id AS id FROM song WHERE song.inLibrary IS NOT NULL " +
            "ORDER BY ((song.rowid * :randomSeed) & 2147483647), song.id LIMIT 10)) " +
            "ORDER BY ((song.rowid * :randomSeed) & 2147483647), song.id LIMIT 100",
    )
    fun quickPicks(
        now: Long = System.currentTimeMillis(),
        randomSeed: Int = Random.nextInt(1, Int.MAX_VALUE) or 1,
    ): Flow<List<Song>>

    @Query("SELECT id FROM song WHERE hideFromQuickPicks = 1")
    fun hiddenFromQuickPicksSongIds(): Flow<List<String>>

    @Transaction
    @Query(
        "SELECT song.* FROM song JOIN event ON event.songId = song.id GROUP BY song.id " +
            "ORDER BY MAX(event.timestamp) DESC, MAX(event.id) DESC LIMIT :limit",
    )
    fun recentlyPlayedSongs(limit: Int = 50): Flow<List<Song>>

    @Transaction
    @Query(
        "SELECT album.*, 0 AS songCountListened, 0 AS timeListened FROM album " +
            "JOIN song ON song.albumId = album.id JOIN event ON event.songId = song.id " +
            "WHERE event.timestamp > :now - 1209600000 AND song.albumId IS NOT NULL " +
            "GROUP BY album.id ORDER BY SUM(event.playTime) DESC LIMIT :limit OFFSET :offset",
    )
    fun recommendedAlbum(now: Long, limit: Int = 5, offset: Int = 0): Flow<List<Album>>

    fun getRecommendationAlbum(now: Long, limit: Int = 5, offset: Int = 0): Flow<List<Album>> =
        recommendedAlbum(now, limit, offset)

    @Query(
        "SELECT song.id AS id, song.title AS title, " +
            "(SELECT artist.name FROM song_artist_map JOIN artist " +
            "ON artist.id = song_artist_map.artistId WHERE song_artist_map.songId = song.id " +
            "ORDER BY song_artist_map.position LIMIT 1) AS artistName, " +
            "song.thumbnailUrl AS thumbnailUrl, COUNT(event.id) AS songCountListened, " +
            "SUM(event.playTime) AS timeListened, song.isVideo AS isVideo " +
            "FROM event JOIN song ON song.id = event.songId " +
            "WHERE event.timestamp > :fromTimeStamp AND event.timestamp <= :toTimeStamp " +
            "GROUP BY song.id ORDER BY SUM(event.playTime) DESC LIMIT :limit OFFSET :offset",
    )
    fun mostPlayedSongsStats(
        fromTimeStamp: Long,
        limit: Int = 6,
        offset: Int = 0,
        toTimeStamp: Long = System.currentTimeMillis(),
    ): Flow<List<SongWithStats>>

    @Transaction
    @Query(
        "SELECT song.* FROM event JOIN song ON song.id = event.songId " +
            "WHERE event.timestamp > :fromTimeStamp AND event.timestamp <= :toTimeStamp " +
            "AND song.hideFromQuickPicks = 0 AND (:hideVideoSongs = 0 OR song.isVideo = 0) " +
            "GROUP BY song.id ORDER BY SUM(event.playTime) DESC LIMIT :limit OFFSET :offset",
    )
    fun mostPlayedSongs(
        fromTimeStamp: Long,
        limit: Int = 6,
        offset: Int = 0,
        toTimeStamp: Long = System.currentTimeMillis(),
        hideVideoSongs: Boolean = false,
    ): Flow<List<Song>>

    @Transaction
    @Query(
        "SELECT song.* FROM event JOIN song ON song.id = event.songId " +
            "WHERE event.timestamp > :fromTimeStamp AND event.timestamp <= :toTimeStamp " +
            "AND song.hideFromQuickPicks = 0 AND (:hideVideoSongs = 0 OR song.isVideo = 0) " +
            "GROUP BY song.id ORDER BY SUM(event.playTime) ASC LIMIT :limit OFFSET :offset",
    )
    fun leastPlayedSongs(
        fromTimeStamp: Long,
        limit: Int = 6,
        offset: Int = 0,
        toTimeStamp: Long = System.currentTimeMillis(),
        hideVideoSongs: Boolean = false,
    ): Flow<List<Song>>

    @Transaction
    @Query(
        "SELECT artist.*, COUNT(DISTINCT song.id) AS songCount, " +
            "CAST(SUM(event.playTime) AS INTEGER) AS timeListened " +
            "FROM event JOIN song_artist_map ON song_artist_map.songId = event.songId " +
            "JOIN artist ON artist.id = song_artist_map.artistId " +
            "WHERE event.timestamp > :fromTimeStamp AND event.timestamp <= :toTimeStamp " +
            "GROUP BY artist.id ORDER BY SUM(event.playTime) DESC LIMIT :limit OFFSET :offset",
    )
    fun mostPlayedArtists(
        fromTimeStamp: Long,
        limit: Int = 6,
        offset: Int = 0,
        toTimeStamp: Long = System.currentTimeMillis(),
    ): Flow<List<Artist>>

    @Transaction
    @Query(
        "SELECT album.*, 0 AS songCountListened, 0 AS timeListened FROM event " +
            "JOIN song ON song.id = event.songId JOIN album ON album.id = song.albumId " +
            "WHERE event.timestamp > :fromTimeStamp AND event.timestamp <= :toTimeStamp " +
            "GROUP BY album.id ORDER BY SUM(event.playTime) DESC LIMIT :limit OFFSET :offset",
    )
    fun mostPlayedAlbums(
        fromTimeStamp: Long,
        limit: Int = 6,
        offset: Int = 0,
        toTimeStamp: Long = System.currentTimeMillis(),
    ): Flow<List<Album>>

    @Transaction
    @Query(
        "SELECT * FROM song WHERE hideFromQuickPicks = 0 AND " +
            "(SELECT COALESCE(SUM(playTime), 0) FROM event WHERE event.songId = song.id " +
            "AND event.timestamp < :now - 2592000000) * 0.2 > " +
            "(SELECT COALESCE(SUM(playTime), 0) FROM event WHERE event.songId = song.id " +
            "AND event.timestamp >= :now - 2592000000) " +
            "ORDER BY ((song.rowid * :randomSeed) & 2147483647), song.id LIMIT 100",
    )
    fun forgottenFavorites(now: Long, randomSeed: Int): Flow<List<Song>>

    @Transaction
    @Query(
        "SELECT album.*, 0 AS songCountListened, 0 AS timeListened FROM album " +
            "JOIN album_artist_map ON album_artist_map.albumId = album.id " +
            "WHERE album_artist_map.artistId = :artistId LIMIT :previewSize",
    )
    fun artistAlbumsPreview(artistId: String, previewSize: Int = 6): Flow<List<Album>>

    @Query(
        "SELECT COALESCE(SUM(count), 0) FROM playCount WHERE song = :songId " +
            "AND year = :year AND month = :month",
    )
    fun getPlayCountByMonth(songId: String?, year: Int, month: Int): Flow<Int>

    @Query("SELECT COALESCE(SUM(count), 0) FROM playCount WHERE song = :songId AND year = :year")
    fun getPlayCountByYear(songId: String?, year: Int): Flow<Int>

    @Query("SELECT COALESCE(SUM(count), 0) FROM playCount WHERE song = :songId")
    fun getLifetimePlayCount(songId: String?): Flow<Int>

    // ------------------------------------------------------------------
    // Statistics
    // ------------------------------------------------------------------

    @Query("SELECT SUM(playTime) FROM event WHERE timestamp >= :from AND timestamp <= :to")
    fun getTotalPlayTimeInRange(from: Long, to: Long): Flow<Long?>

    @Query(
        "SELECT COUNT(DISTINCT songId) FROM event WHERE timestamp >= :from AND timestamp <= :to",
    )
    fun getUniqueSongCountInRange(from: Long, to: Long): Flow<Int>

    @Query(
        "SELECT COUNT(DISTINCT song_artist_map.artistId) FROM event " +
            "JOIN song_artist_map ON song_artist_map.songId = event.songId " +
            "WHERE event.timestamp >= :from AND event.timestamp <= :to",
    )
    fun getUniqueArtistCountInRange(from: Long, to: Long): Flow<Int>

    @Query(
        "SELECT COUNT(DISTINCT song.albumId) FROM event JOIN song ON song.id = event.songId " +
            "WHERE event.timestamp >= :from AND event.timestamp <= :to",
    )
    fun getUniqueAlbumCountInRange(from: Long, to: Long): Flow<Int>

    @Query(
        "SELECT COALESCE(SUM(playTime), 0) FROM event WHERE timestamp >= :from AND timestamp < :to",
    )
    fun getPlayTimeForDay(from: Long, to: Long): Flow<Long>

    @Query(
        "SELECT COALESCE(SUM(playTime), 0) FROM event WHERE timestamp >= :from AND timestamp < :to",
    )
    fun getSongsPlayTimeForDay(from: Long, to: Long): Flow<Long>

    @Query(
        "SELECT COALESCE(SUM(event.playTime), 0) FROM event " +
            "JOIN song_artist_map ON song_artist_map.songId = event.songId " +
            "WHERE event.timestamp >= :from AND event.timestamp < :to",
    )
    fun getArtistPlayTimeForDay(from: Long, to: Long): Flow<Long>

    @Query(
        "SELECT COALESCE(SUM(event.playTime), 0) FROM event " +
            "JOIN song ON song.id = event.songId " +
            "WHERE event.timestamp >= :from AND event.timestamp < :to",
    )
    fun getAlbumPlayTimeForDay(from: Long, to: Long): Flow<Long>

    // ------------------------------------------------------------------
    // Single-item lookups
    // ------------------------------------------------------------------

    @Transaction
    @Query("SELECT * FROM song WHERE id = :songId")
    fun song(songId: String?): Flow<Song?>

    @Transaction
    @Query("SELECT * FROM song WHERE id = :songId")
    suspend fun getSongById(songId: String): Song?

    @Transaction
    @Query("SELECT * FROM song WHERE id = :songId")
    fun getSongByIdBlocking(songId: String): Song?

    @Transaction
    @Query("SELECT * FROM song WHERE id IN (:songIds)")
    suspend fun getSongsByIds(songIds: List<String>): List<Song>

    @Transaction
    @Query("SELECT * FROM song WHERE id IN (:songIds)")
    fun getSongsByIdsFlow(songIds: List<String>): Flow<List<Song>>

    @Query("SELECT * FROM song_artist_map WHERE songId = :songId")
    fun songArtistMap(songId: String): List<SongArtistMap>

    @Transaction
    @Query("SELECT * FROM song")
    fun allSongs(): Flow<List<Song>>

    @Transaction
    @Query(
        "SELECT * FROM song WHERE inLibrary IS NOT NULL OR liked = 1 " +
            "OR id IN (SELECT songId FROM playlist_song_map) ORDER BY rowid DESC",
    )
    fun carouselLibrarySongs(): Flow<List<Song>>

    @Transaction
    @Query(
        "SELECT artist.*, " +
            "(SELECT COUNT(*) FROM song_artist_map JOIN song " +
            "ON song.id = song_artist_map.songId WHERE song_artist_map.artistId = artist.id " +
            "AND song.inLibrary IS NOT NULL) AS songCount, " +
            "CAST(COALESCE((SELECT SUM(event.playTime) FROM event " +
            "JOIN song_artist_map ON song_artist_map.songId = event.songId " +
            "WHERE song_artist_map.artistId = artist.id), 0) AS INTEGER) AS timeListened " +
            "FROM artist ORDER BY (timeListened > 0) DESC, timeListened DESC",
    )
    fun allArtistsByPlayTime(): Flow<List<Artist>>

    @Query("SELECT * FROM set_video_id WHERE videoId = :videoId LIMIT 1")
    suspend fun getSetVideoId(videoId: String): SetVideoIdEntity?

    @Query("SELECT * FROM format WHERE id = :id LIMIT 1")
    fun format(id: String?): Flow<FormatEntity?>

    @Query("SELECT * FROM lyrics WHERE id = :id LIMIT 1")
    fun lyrics(id: String?): Flow<LyricsEntity?>

    @Query(
        "SELECT artist.*, (SELECT COUNT(*) FROM song_artist_map JOIN song " +
            "ON song.id = song_artist_map.songId WHERE song_artist_map.artistId = artist.id " +
            "AND song.inLibrary IS NOT NULL) AS songCount, 0 AS timeListened " +
            "FROM artist WHERE id = :id LIMIT 1",
    )
    fun artist(id: String): Flow<Artist?>

    @Transaction
    @Query("SELECT album.*, 0 AS songCountListened, 0 AS timeListened FROM album WHERE id = :id LIMIT 1")
    fun album(id: String): Flow<Album?>

    @Transaction
    @Query("SELECT * FROM album WHERE id = :albumId LIMIT 1")
    fun albumWithSongs(albumId: String): Flow<AlbumWithSongs?>

    @Query("SELECT * FROM album_artist_map WHERE albumId = :albumId")
    fun albumArtistMaps(albumId: String): List<AlbumArtistMap>

    @Transaction
    @Query(
        "SELECT playlist.*, (SELECT COUNT(*) FROM playlist_song_map " +
            "WHERE playlist_song_map.playlistId = playlist.id) AS songCount " +
            "FROM playlist WHERE id = :playlistId LIMIT 1",
    )
    fun playlist(playlistId: String): Flow<Playlist?>

    @Transaction
    @Query(
        "SELECT playlist.*, (SELECT COUNT(*) FROM playlist_song_map " +
            "WHERE playlist_song_map.playlistId = playlist.id) AS songCount " +
            "FROM playlist WHERE id = :playlistId LIMIT 1",
    )
    suspend fun getPlaylistById(playlistId: String): Playlist?

    @Transaction
    @Query(
        "SELECT playlist.*, (SELECT COUNT(*) FROM playlist_song_map " +
            "WHERE playlist_song_map.playlistId = playlist.id) AS songCount " +
            "FROM playlist WHERE id = :playlistId LIMIT 1",
    )
    fun getPlaylistByIdBlocking(playlistId: String): Playlist?

    @Transaction
    @Query(
        "SELECT playlist.*, (SELECT COUNT(*) FROM playlist_song_map " +
            "WHERE playlist_song_map.playlistId = playlist.id) AS songCount " +
            "FROM playlist WHERE browseId = :browseId LIMIT 1",
    )
    fun playlistByBrowseId(browseId: String): Flow<Playlist?>

    @Query("SELECT * FROM beat_info WHERE songId = :songId LIMIT 1")
    fun beatInfo(songId: String): BeatInfoEntity?

    @Query("SELECT * FROM artist WHERE id = :id LIMIT 1")
    suspend fun getArtistById(id: String): ArtistEntity?

    @Query("SELECT * FROM artist WHERE name = :name LIMIT 1")
    fun artistByName(name: String): ArtistEntity?

    // ------------------------------------------------------------------
    // Artist listings
    // ------------------------------------------------------------------

    @Query(
        "SELECT artist.*, (SELECT COUNT(*) FROM song_artist_map JOIN song " +
            "ON song.id = song_artist_map.songId WHERE song_artist_map.artistId = artist.id " +
            "AND song.inLibrary IS NOT NULL) AS songCount, 0 AS timeListened FROM artist " +
            "WHERE (SELECT COUNT(*) FROM song_artist_map JOIN song " +
            "ON song.id = song_artist_map.songId WHERE song_artist_map.artistId = artist.id " +
            "AND song.inLibrary IS NOT NULL) > 0 ORDER BY artist.rowid ASC",
    )
    fun artistsByCreateDateAsc(): Flow<List<Artist>>

    @Query(
        "SELECT artist.*, (SELECT COUNT(*) FROM song_artist_map JOIN song " +
            "ON song.id = song_artist_map.songId WHERE song_artist_map.artistId = artist.id " +
            "AND song.inLibrary IS NOT NULL) AS songCount, 0 AS timeListened FROM artist " +
            "WHERE (SELECT COUNT(*) FROM song_artist_map JOIN song " +
            "ON song.id = song_artist_map.songId WHERE song_artist_map.artistId = artist.id " +
            "AND song.inLibrary IS NOT NULL) > 0 ORDER BY artist.name COLLATE NOCASE ASC",
    )
    fun artistsByNameAsc(): Flow<List<Artist>>

    @Query(
        "SELECT artist.*, (SELECT COUNT(*) FROM song_artist_map JOIN song " +
            "ON song.id = song_artist_map.songId WHERE song_artist_map.artistId = artist.id " +
            "AND song.inLibrary IS NOT NULL) AS songCount, 0 AS timeListened FROM artist " +
            "WHERE (SELECT COUNT(*) FROM song_artist_map JOIN song " +
            "ON song.id = song_artist_map.songId WHERE song_artist_map.artistId = artist.id " +
            "AND song.inLibrary IS NOT NULL) > 0 ORDER BY songCount ASC",
    )
    fun artistsBySongCountAsc(): Flow<List<Artist>>

    @Query(
        "SELECT artist.*, (SELECT COUNT(*) FROM song_artist_map JOIN song " +
            "ON song.id = song_artist_map.songId WHERE song_artist_map.artistId = artist.id " +
            "AND song.inLibrary IS NOT NULL) AS songCount, " +
            "CAST(COALESCE((SELECT SUM(song.totalPlayTime) FROM song_artist_map JOIN song " +
            "ON song.id = song_artist_map.songId WHERE song_artist_map.artistId = artist.id " +
            "AND song.inLibrary IS NOT NULL), 0) AS INTEGER) AS timeListened FROM artist " +
            "WHERE (SELECT COUNT(*) FROM song_artist_map JOIN song " +
            "ON song.id = song_artist_map.songId WHERE song_artist_map.artistId = artist.id " +
            "AND song.inLibrary IS NOT NULL) > 0 ORDER BY timeListened ASC",
    )
    fun artistsByPlayTimeAsc(): Flow<List<Artist>>

    @Query(
        "SELECT artist.*, (SELECT COUNT(*) FROM song_artist_map JOIN song " +
            "ON song.id = song_artist_map.songId WHERE song_artist_map.artistId = artist.id " +
            "AND song.inLibrary IS NOT NULL) AS songCount, 0 AS timeListened FROM artist " +
            "WHERE bookmarkedAt IS NOT NULL ORDER BY artist.rowid ASC",
    )
    fun artistsBookmarkedByCreateDateAsc(): Flow<List<Artist>>

    @Query(
        "SELECT artist.*, (SELECT COUNT(*) FROM song_artist_map JOIN song " +
            "ON song.id = song_artist_map.songId WHERE song_artist_map.artistId = artist.id " +
            "AND song.inLibrary IS NOT NULL) AS songCount, 0 AS timeListened FROM artist " +
            "WHERE bookmarkedAt IS NOT NULL ORDER BY artist.name COLLATE NOCASE ASC",
    )
    fun artistsBookmarkedByNameAsc(): Flow<List<Artist>>

    @Query(
        "SELECT artist.*, (SELECT COUNT(*) FROM song_artist_map JOIN song " +
            "ON song.id = song_artist_map.songId WHERE song_artist_map.artistId = artist.id " +
            "AND song.inLibrary IS NOT NULL) AS songCount, 0 AS timeListened FROM artist " +
            "WHERE bookmarkedAt IS NOT NULL ORDER BY songCount ASC",
    )
    fun artistsBookmarkedBySongCountAsc(): Flow<List<Artist>>

    @Query(
        "SELECT artist.*, (SELECT COUNT(*) FROM song_artist_map JOIN song " +
            "ON song.id = song_artist_map.songId WHERE song_artist_map.artistId = artist.id " +
            "AND song.inLibrary IS NOT NULL) AS songCount, " +
            "CAST(COALESCE((SELECT SUM(song.totalPlayTime) FROM song_artist_map JOIN song " +
            "ON song.id = song_artist_map.songId WHERE song_artist_map.artistId = artist.id " +
            "AND song.inLibrary IS NOT NULL), 0) AS INTEGER) AS timeListened FROM artist " +
            "WHERE bookmarkedAt IS NOT NULL ORDER BY timeListened ASC",
    )
    fun artistsBookmarkedByPlayTimeAsc(): Flow<List<Artist>>

    // ------------------------------------------------------------------
    // Album listings
    // ------------------------------------------------------------------

    @Transaction
    @Query(
        "SELECT album.*, 0 AS songCountListened, 0 AS timeListened FROM album " +
            "WHERE EXISTS (SELECT 1 FROM song_album_map JOIN song " +
            "ON song.id = song_album_map.songId WHERE song_album_map.albumId = album.id " +
            "AND song.inLibrary IS NOT NULL) ORDER BY album.rowid ASC",
    )
    fun albumsByCreateDateAsc(): Flow<List<Album>>

    @Transaction
    @Query(
        "SELECT album.*, 0 AS songCountListened, 0 AS timeListened FROM album " +
            "WHERE EXISTS (SELECT 1 FROM song_album_map JOIN song " +
            "ON song.id = song_album_map.songId WHERE song_album_map.albumId = album.id " +
            "AND song.inLibrary IS NOT NULL) ORDER BY album.title COLLATE NOCASE ASC",
    )
    fun albumsByNameAsc(): Flow<List<Album>>

    @Transaction
    @Query(
        "SELECT album.*, 0 AS songCountListened, 0 AS timeListened FROM album " +
            "WHERE EXISTS (SELECT 1 FROM song_album_map JOIN song " +
            "ON song.id = song_album_map.songId WHERE song_album_map.albumId = album.id " +
            "AND song.inLibrary IS NOT NULL) ORDER BY " +
            "(SELECT GROUP_CONCAT(artist.name, ', ') FROM album_artist_map JOIN artist " +
            "ON artist.id = album_artist_map.artistId WHERE album_artist_map.albumId = album.id) ASC",
    )
    fun albumsByArtistAsc(): Flow<List<Album>>

    @Transaction
    @Query(
        "SELECT album.*, 0 AS songCountListened, 0 AS timeListened FROM album " +
            "WHERE EXISTS (SELECT 1 FROM song_album_map JOIN song " +
            "ON song.id = song_album_map.songId WHERE song_album_map.albumId = album.id " +
            "AND song.inLibrary IS NOT NULL) ORDER BY album.year ASC",
    )
    fun albumsByYearAsc(): Flow<List<Album>>

    @Transaction
    @Query(
        "SELECT album.*, 0 AS songCountListened, 0 AS timeListened FROM album " +
            "WHERE EXISTS (SELECT 1 FROM song_album_map JOIN song " +
            "ON song.id = song_album_map.songId WHERE song_album_map.albumId = album.id " +
            "AND song.inLibrary IS NOT NULL) ORDER BY album.songCount ASC",
    )
    fun albumsBySongCountAsc(): Flow<List<Album>>

    @Transaction
    @Query(
        "SELECT album.*, 0 AS songCountListened, 0 AS timeListened FROM album " +
            "WHERE EXISTS (SELECT 1 FROM song_album_map JOIN song " +
            "ON song.id = song_album_map.songId WHERE song_album_map.albumId = album.id " +
            "AND song.inLibrary IS NOT NULL) ORDER BY album.duration ASC",
    )
    fun albumsByLengthAsc(): Flow<List<Album>>

    @Transaction
    @Query(
        "SELECT album.*, 0 AS songCountListened, 0 AS timeListened FROM album " +
            "WHERE EXISTS (SELECT 1 FROM song_album_map JOIN song " +
            "ON song.id = song_album_map.songId WHERE song_album_map.albumId = album.id " +
            "AND song.inLibrary IS NOT NULL) ORDER BY " +
            "(SELECT COALESCE(SUM(song.totalPlayTime), 0) FROM song_album_map JOIN song " +
            "ON song.id = song_album_map.songId WHERE song_album_map.albumId = album.id " +
            "AND song.inLibrary IS NOT NULL) ASC",
    )
    fun albumsByPlayTimeAsc(): Flow<List<Album>>

    @Transaction
    @Query(
        "SELECT album.*, 0 AS songCountListened, 0 AS timeListened FROM album " +
            "WHERE bookmarkedAt IS NOT NULL ORDER BY album.rowid ASC",
    )
    fun albumsLikedByCreateDateAsc(): Flow<List<Album>>

    @Transaction
    @Query(
        "SELECT album.*, 0 AS songCountListened, 0 AS timeListened FROM album " +
            "WHERE bookmarkedAt IS NOT NULL ORDER BY album.title COLLATE NOCASE ASC",
    )
    fun albumsLikedByNameAsc(): Flow<List<Album>>

    @Transaction
    @Query(
        "SELECT album.*, 0 AS songCountListened, 0 AS timeListened FROM album " +
            "WHERE bookmarkedAt IS NOT NULL ORDER BY (SELECT GROUP_CONCAT(artist.name, ', ') " +
            "FROM album_artist_map JOIN artist ON artist.id = album_artist_map.artistId " +
            "WHERE album_artist_map.albumId = album.id) ASC",
    )
    fun albumsLikedByArtistAsc(): Flow<List<Album>>

    @Transaction
    @Query(
        "SELECT album.*, 0 AS songCountListened, 0 AS timeListened FROM album " +
            "WHERE bookmarkedAt IS NOT NULL ORDER BY album.year ASC",
    )
    fun albumsLikedByYearAsc(): Flow<List<Album>>

    @Transaction
    @Query(
        "SELECT album.*, 0 AS songCountListened, 0 AS timeListened FROM album " +
            "WHERE bookmarkedAt IS NOT NULL ORDER BY album.songCount ASC",
    )
    fun albumsLikedBySongCountAsc(): Flow<List<Album>>

    @Transaction
    @Query(
        "SELECT album.*, 0 AS songCountListened, 0 AS timeListened FROM album " +
            "WHERE bookmarkedAt IS NOT NULL ORDER BY album.duration ASC",
    )
    fun albumsLikedByLengthAsc(): Flow<List<Album>>

    @Transaction
    @Query(
        "SELECT album.*, 0 AS songCountListened, 0 AS timeListened FROM album " +
            "WHERE bookmarkedAt IS NOT NULL ORDER BY (SELECT COALESCE(SUM(song.totalPlayTime), 0) " +
            "FROM song_album_map JOIN song ON song.id = song_album_map.songId " +
            "WHERE song_album_map.albumId = album.id AND song.inLibrary IS NOT NULL) ASC",
    )
    fun albumsLikedByPlayTimeAsc(): Flow<List<Album>>

    @Transaction
    @Query(
        "SELECT album.*, 0 AS songCountListened, 0 AS timeListened FROM album " +
            "WHERE isUploaded = 1 ORDER BY album.rowid ASC",
    )
    fun albumsUploadedByCreateDateAsc(): Flow<List<Album>>

    @Transaction
    @Query(
        "SELECT album.*, 0 AS songCountListened, 0 AS timeListened FROM album " +
            "WHERE bookmarkedAt IS NOT NULL ORDER BY album.title COLLATE NOCASE ASC",
    )
    fun albumsUploadedByNameAsc(): Flow<List<Album>>

    @Transaction
    @Query(
        "SELECT album.*, 0 AS songCountListened, 0 AS timeListened FROM album " +
            "WHERE bookmarkedAt IS NOT NULL ORDER BY (SELECT GROUP_CONCAT(artist.name, ', ') " +
            "FROM album_artist_map JOIN artist ON artist.id = album_artist_map.artistId " +
            "WHERE album_artist_map.albumId = album.id) ASC",
    )
    fun albumsUploadedByArtistAsc(): Flow<List<Album>>

    @Transaction
    @Query(
        "SELECT album.*, 0 AS songCountListened, 0 AS timeListened FROM album " +
            "WHERE bookmarkedAt IS NOT NULL ORDER BY album.year ASC",
    )
    fun albumsUploadedByYearAsc(): Flow<List<Album>>

    @Transaction
    @Query(
        "SELECT album.*, 0 AS songCountListened, 0 AS timeListened FROM album " +
            "WHERE bookmarkedAt IS NOT NULL ORDER BY album.songCount ASC",
    )
    fun albumsUploadedBySongCountAsc(): Flow<List<Album>>

    @Transaction
    @Query(
        "SELECT album.*, 0 AS songCountListened, 0 AS timeListened FROM album " +
            "WHERE bookmarkedAt IS NOT NULL ORDER BY album.duration ASC",
    )
    fun albumsUploadedByLengthAsc(): Flow<List<Album>>

    @Transaction
    @Query(
        "SELECT album.*, 0 AS songCountListened, 0 AS timeListened FROM album " +
            "WHERE bookmarkedAt IS NOT NULL ORDER BY (SELECT COALESCE(SUM(song.totalPlayTime), 0) " +
            "FROM song_album_map JOIN song ON song.id = song_album_map.songId " +
            "WHERE song_album_map.albumId = album.id AND song.inLibrary IS NOT NULL) ASC",
    )
    fun albumsUploadedByPlayTimeAsc(): Flow<List<Album>>

    // ------------------------------------------------------------------
    // Playlist listings
    // ------------------------------------------------------------------

    @Transaction
    @Query(
        "SELECT playlist.*, (SELECT COUNT(*) FROM playlist_song_map " +
            "WHERE playlist_song_map.playlistId = playlist.id) AS songCount " +
            "FROM playlist ORDER BY playlist.rowid ASC",
    )
    fun playlistsByCreateDateAsc(): Flow<List<Playlist>>

    @Transaction
    @Query(
        "SELECT playlist.*, (SELECT COUNT(*) FROM playlist_song_map " +
            "WHERE playlist_song_map.playlistId = playlist.id) AS songCount " +
            "FROM playlist ORDER BY playlist.name COLLATE NOCASE ASC",
    )
    fun playlistsByNameAsc(): Flow<List<Playlist>>

    @Transaction
    @Query(
        "SELECT playlist.*, (SELECT COUNT(*) FROM playlist_song_map " +
            "WHERE playlist_song_map.playlistId = playlist.id) AS songCount " +
            "FROM playlist ORDER BY songCount ASC",
    )
    fun playlistsBySongCountAsc(): Flow<List<Playlist>>

    @Transaction
    @Query(
        "SELECT playlist.*, (SELECT COUNT(*) FROM playlist_song_map " +
            "WHERE playlist_song_map.playlistId = playlist.id) AS songCount " +
            "FROM playlist ORDER BY playlist.lastUpdateTime ASC",
    )
    fun playlistsByLastUpdatedAsc(): Flow<List<Playlist>>

    // ------------------------------------------------------------------
    // Playlist membership
    // ------------------------------------------------------------------

    @Query(
        "SELECT EXISTS(SELECT 1 FROM playlist_song_map " +
            "WHERE playlistId = :playlistId AND songId = :songId)",
    )
    fun checkInPlaylist(playlistId: String, songId: String): Int

    @Query("SELECT songId FROM playlist_song_map WHERE playlistId = :playlistId AND songId IN (:songIds)")
    fun playlistDuplicates(playlistId: String, songIds: List<String>): List<String>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_song_map WHERE playlistId = :playlistId")
    fun nextPlaylistPosition(playlistId: String): Int

    @Transaction
    suspend fun addSongToPlaylist(playlist: Playlist, songIds: List<String>) {
        var position = nextPlaylistPosition(playlist.id)
        songIds.forEach { songId ->
            insert(PlaylistSongMap(playlistId = playlist.id, songId = songId, position = position))
            position++
        }
    }

    @Transaction
    suspend fun move(playlistId: String, fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition) return
        val low = minOf(fromPosition, toPosition)
        val high = maxOf(fromPosition, toPosition)
        val affected = playlistSongMapsInRange(playlistId, low, high).toMutableList()
        val moving = affected.firstOrNull { it.position == fromPosition } ?: return
        affected.forEach { map ->
            val shifted = when {
                map.position == fromPosition -> toPosition
                fromPosition < toPosition && map.position > fromPosition && map.position <= toPosition ->
                    map.position - 1
                fromPosition > toPosition && map.position >= toPosition && map.position < fromPosition ->
                    map.position + 1
                else -> map.position
            }
            if (shifted != map.position) update(map.copy(position = shifted))
        }
        if (moving.position != toPosition) update(moving.copy(position = toPosition))
    }

    @Query(
        "SELECT * FROM playlist_song_map WHERE playlistId = :playlistId " +
            "AND position BETWEEN :from AND :to ORDER BY position ASC",
    )
    fun playlistSongMapsInRange(playlistId: String, from: Int, to: Int): List<PlaylistSongMap>

    @Query("DELETE FROM playlist_song_map WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: String)

    @Query("SELECT * FROM playlist_song_map WHERE songId = :songId")
    fun playlistSongMaps(songId: String): List<PlaylistSongMap>

    @Query("SELECT * FROM playlist_song_map WHERE playlistId = :playlistId AND position >= :from ORDER BY position ASC")
    fun playlistSongMaps(playlistId: String, from: Int): List<PlaylistSongMap>

    // ------------------------------------------------------------------
    // Download and upload state
    // ------------------------------------------------------------------

    @Transaction
    @Query(
        "SELECT * FROM song WHERE isDownloaded = 1 ORDER BY dateDownload ASC, rowid ASC",
    )
    fun downloadedSongsByCreateDateAsc(): Flow<List<Song>>

    @Transaction
    @Query(
        "SELECT * FROM song WHERE isDownloaded = 1 ORDER BY title COLLATE NOCASE ASC, id ASC",
    )
    fun downloadedSongsByNameAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE isDownloaded = 1 ORDER BY totalPlayTime ASC")
    fun downloadedSongsByPlayTimeAsc(): Flow<List<Song>>

    @Query("UPDATE song SET isDownloaded = :downloaded, dateDownload = :date WHERE id = :songId")
    fun updateDownloadedInfo(songId: String, downloaded: Boolean, date: LocalDateTime?)

    @Transaction
    @Query("SELECT * FROM song WHERE isUploaded = 1 ORDER BY dateDownload ASC, rowid ASC")
    fun uploadedSongsByCreateDateAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE isUploaded = 1 ORDER BY title COLLATE NOCASE ASC, id ASC")
    fun uploadedSongsByNameAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE isUploaded = 1 ORDER BY totalPlayTime ASC")
    fun uploadedSongsByPlayTimeAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE isUploaded = 1 ORDER BY rowid ASC")
    fun uploadedSongsByRowIdAsc(): Flow<List<Song>>

    // ------------------------------------------------------------------
    // Search
    // ------------------------------------------------------------------

    @Transaction
    @Query(
        "SELECT * FROM song WHERE title LIKE '%' || :query || '%' AND inLibrary IS NOT NULL " +
            "LIMIT :previewSize",
    )
    fun searchSongs(query: String, previewSize: Int = Int.MAX_VALUE): Flow<List<Song>>

    @Query(
        "SELECT artist.*, (SELECT COUNT(*) FROM song_artist_map JOIN song " +
            "ON song.id = song_artist_map.songId WHERE song_artist_map.artistId = artist.id " +
            "AND song.inLibrary IS NOT NULL) AS songCount, 0 AS timeListened FROM artist " +
            "WHERE name LIKE '%' || :query || '%' AND (SELECT COUNT(*) FROM song_artist_map " +
            "JOIN song ON song.id = song_artist_map.songId " +
            "WHERE song_artist_map.artistId = artist.id AND song.inLibrary IS NOT NULL) > 0 " +
            "LIMIT :previewSize",
    )
    fun searchArtists(query: String, previewSize: Int = Int.MAX_VALUE): Flow<List<Artist>>

    @Transaction
    @Query(
        "SELECT album.*, 0 AS songCountListened, 0 AS timeListened FROM album " +
            "WHERE title LIKE '%' || :query || '%' AND EXISTS (SELECT 1 FROM song_album_map " +
            "JOIN song ON song.id = song_album_map.songId " +
            "WHERE song_album_map.albumId = album.id AND song.inLibrary IS NOT NULL) " +
            "LIMIT :previewSize",
    )
    fun searchAlbums(query: String, previewSize: Int = Int.MAX_VALUE): Flow<List<Album>>

    @Transaction
    @Query(
        "SELECT playlist.*, (SELECT COUNT(*) FROM playlist_song_map " +
            "WHERE playlist_song_map.playlistId = playlist.id) AS songCount FROM playlist " +
            "WHERE name LIKE '%' || :query || '%' LIMIT :previewSize",
    )
    fun searchPlaylists(query: String, previewSize: Int = Int.MAX_VALUE): Flow<List<Playlist>>

    // ------------------------------------------------------------------
    // History
    // ------------------------------------------------------------------

    @Transaction
    @Query("SELECT * FROM event ORDER BY id DESC")
    fun events(): Flow<List<EventWithSong>>

    @Transaction
    @Query("SELECT * FROM event WHERE timestamp >= :from AND timestamp < :to ORDER BY id DESC")
    fun eventsForPeriod(from: Long, to: Long): Flow<List<EventWithSong>>

    @Transaction
    @Query("SELECT * FROM event ORDER BY id ASC LIMIT 1")
    fun firstEvent(): Flow<EventWithSong?>

    @Query("SELECT COUNT(*) FROM event")
    fun eventCount(): Flow<Int>

    @Query("DELETE FROM event")
    suspend fun clearListenHistory()

    @Query("SELECT * FROM search_history WHERE query LIKE :query || '%' ORDER BY id DESC")
    fun searchHistory(query: String = ""): Flow<List<SearchHistory>>

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    @Query("SELECT * FROM recognition_history ORDER BY recognizedAt DESC")
    fun recognitionHistory(): Flow<List<RecognitionHistory>>

    @Query("SELECT * FROM recognition_history WHERE id = :id LIMIT 1")
    fun recognitionHistoryById(id: Long): Flow<RecognitionHistory?>

    @Query("SELECT * FROM recognition_history WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' ORDER BY recognizedAt DESC")
    fun searchRecognitionHistory(query: String): Flow<List<RecognitionHistory>>

    @Query("DELETE FROM recognition_history")
    suspend fun clearRecognitionHistory()

    @Query("DELETE FROM recognition_history WHERE id = :id")
    suspend fun deleteRecognitionHistoryById(id: Long)

    @Query("UPDATE recognition_history SET liked = :liked WHERE id = :id")
    suspend fun updateRecognitionHistoryLiked(id: Long, liked: Boolean)

    // ------------------------------------------------------------------
    // Counters
    // ------------------------------------------------------------------

    @Query("UPDATE song SET totalPlayTime = totalPlayTime + :playTime WHERE id = :songId")
    suspend fun incrementTotalPlayTime(songId: String, playTime: Long)

    @Query("UPDATE song SET liked = :liked, likedDate = :likedDate WHERE id = :songId")
    suspend fun updateLikedStatus(songId: String, liked: Boolean, likedDate: LocalDateTime?)

    @Query("UPDATE playCount SET count = count + 1 WHERE song = :songId AND year = :year AND month = :month")
    suspend fun incrementPlayCount(songId: String, year: Int, month: Int)

    @Query("SELECT * FROM playCount WHERE song = :songId AND year = :year AND month = :month LIMIT 1")
    suspend fun playCountRow(songId: String, year: Int, month: Int): PlayCountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replacePlayCount(playCount: PlayCountEntity)

    @Transaction
    suspend fun incrementPlayCount(songId: String) {
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val previous = playCountRow(songId, now.year, now.monthValue)?.count ?: 0
        replacePlayCount(PlayCountEntity(songId, now.year, now.monthValue, previous + 1))
    }

    @Query("UPDATE song SET inLibrary = :inLibrary WHERE id = :songId")
    suspend fun inLibrary(songId: String, inLibrary: LocalDateTime?)

    @Query("UPDATE song SET libraryAddToken = :libraryAddToken, libraryRemoveToken = :libraryRemoveToken WHERE id = :songId")
    suspend fun addLibraryTokens(songId: String, libraryAddToken: String?, libraryRemoveToken: String?)

    // ------------------------------------------------------------------
    // Related songs
    // ------------------------------------------------------------------

    @Query("SELECT EXISTS(SELECT 1 FROM related_song_map WHERE songId = :songId)")
    fun hasRelatedSongs(songId: String): Boolean

    @Transaction
    @Query(
        "SELECT DISTINCT song.* FROM song JOIN related_song_map " +
            "ON related_song_map.relatedSongId = song.id WHERE related_song_map.songId = :songId",
    )
    fun getRelatedSongs(songId: String): Flow<List<Song>>

    @Transaction
    @Query(
        "SELECT DISTINCT song.* FROM song JOIN related_song_map " +
            "ON related_song_map.relatedSongId = song.id WHERE related_song_map.songId = :songId",
    )
    fun relatedSongs(songId: String): List<Song>

    // ------------------------------------------------------------------
    // Insert / update / upsert / delete
    // ------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(song: SongEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(artist: ArtistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(album: AlbumEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertSongArtistMap(map: SongArtistMap): Long

    fun insert(map: SongArtistMap) {
        runCatching { insertSongArtistMap(map) }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertSongAlbumMap(map: SongAlbumMap): Long

    fun insert(map: SongAlbumMap) {
        runCatching { insertSongAlbumMap(map) }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAlbumArtistMap(map: AlbumArtistMap): Long

    fun insert(map: AlbumArtistMap) {
        runCatching { insertAlbumArtistMap(map) }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertPlaylistSongMap(map: PlaylistSongMap): Long

    fun insert(map: PlaylistSongMap) {
        runCatching { insertPlaylistSongMap(map) }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertRelatedSongMap(map: RelatedSongMap): Long

    fun insert(map: RelatedSongMap) {
        runCatching { insertRelatedSongMap(map) }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(history: SearchHistory): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(event: Event): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(playCount: PlayCountEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(recognitionHistory: RecognitionHistory): Long

    @Upsert
    fun upsert(song: SongEntity)

    @Upsert
    fun upsert(songAlbumMap: SongAlbumMap)

    @Upsert
    fun upsert(lyrics: LyricsEntity)

    @Upsert
    fun upsert(format: FormatEntity)

    @Upsert
    fun upsert(beatInfo: BeatInfoEntity)

    @Update
    fun update(song: SongEntity)

    @Update
    fun update(artist: ArtistEntity)

    @Update
    fun update(album: AlbumEntity)

    @Update
    fun update(playlist: PlaylistEntity)

    @Update
    fun update(playlistSongMap: PlaylistSongMap)

    @Delete
    fun delete(song: SongEntity)

    @Delete
    fun delete(artist: ArtistEntity)

    @Delete
    fun delete(album: AlbumEntity)

    @Delete
    fun delete(playlist: PlaylistEntity)

    @Delete
    fun delete(playlistSongMap: PlaylistSongMap)

    @Delete
    fun delete(songArtistMap: SongArtistMap)

    @Delete
    fun delete(albumArtistMap: AlbumArtistMap)

    @Delete
    fun delete(lyrics: LyricsEntity)

    @Delete
    fun delete(history: SearchHistory)

    @Delete
    fun delete(event: Event)

    @Query("DELETE FROM format WHERE id = :id")
    suspend fun deleteFormat(id: String)

    @Query("DELETE FROM playlist WHERE browseId = :browseId")
    suspend fun deletePlaylistById(browseId: String)

    // ------------------------------------------------------------------
    // Composite operations
    // ------------------------------------------------------------------

    @Transaction
    suspend fun insert(mediaMetadata: MediaMetadata, block: (SongEntity) -> SongEntity = { it }) {
        val entity = block(mediaMetadata.toSongEntity())
        if (insert(entity) == -1L) return
        mediaMetadata.artists.forEachIndexed { index, artist ->
            val artistId = ensureArtist(artist)
            insert(SongArtistMap(songId = entity.id, artistId = artistId, position = index))
        }
    }

    @Transaction
    suspend fun insert(albumPage: AlbumPage) {
        val albumItem = albumPage.album
        val entity = AlbumEntity(
            id = albumItem.browseId,
            playlistId = albumItem.playlistId,
            title = albumItem.title,
            year = albumItem.year,
            thumbnailUrl = albumItem.thumbnail,
            songCount = albumPage.songs.size,
            duration = albumPage.songs.sumOf { it.duration ?: 0 },
            explicit = albumItem.explicit || albumPage.songs.any { it.explicit },
            description = albumPage.description,
        )
        if (insert(entity) == -1L) return
        albumPage.songs.forEachIndexed { index, songItem ->
            val metadata = songItem.toMediaMetadata()
            if (getSongByIdBlocking(metadata.id) == null) insert(metadata) else upsert(metadata.toSongEntity())
            insert(SongAlbumMap(songId = metadata.id, albumId = entity.id, index = index))
        }
        albumItem.artists?.forEachIndexed { index, artist ->
            val artistId = ensureArtist(MediaMetadata.Artist(artist.id, artist.name))
            insert(AlbumArtistMap(albumId = entity.id, artistId = artistId, order = index))
        }
    }

    @Transaction
    suspend fun update(song: Song, mediaMetadata: MediaMetadata) {
        update(
            song.song.copy(
                title = mediaMetadata.title,
                duration = mediaMetadata.duration,
                thumbnailUrl = mediaMetadata.thumbnailUrl,
                albumId = mediaMetadata.album?.id,
                albumName = mediaMetadata.album?.title,
                libraryAddToken = mediaMetadata.libraryAddToken,
                libraryRemoveToken = mediaMetadata.libraryRemoveToken,
            ),
        )
        deleteSongArtistMaps(song.id)
        mediaMetadata.artists.forEachIndexed { index, artist ->
            val artistId = ensureArtist(artist)
            insert(SongArtistMap(songId = song.id, artistId = artistId, position = index))
        }
    }

    @Transaction
    suspend fun update(artist: ArtistEntity, artistPage: ArtistPage) {
        update(
            artist.copy(
                name = artistPage.artist.title,
                thumbnailUrl = artistPage.artist.thumbnail?.resize(544, 544),
                lastUpdateTime = LocalDateTime.now(),
            ),
        )
    }

    @Transaction
    suspend fun update(
        album: AlbumEntity,
        albumPage: AlbumPage,
        artists: List<ArtistEntity> = emptyList(),
    ) {
        val remote = albumPage.album
        val entity = album.copy(
            playlistId = remote.playlistId,
            title = remote.title,
            year = remote.year,
            thumbnailUrl = remote.thumbnail,
            songCount = albumPage.songs.size,
            duration = albumPage.songs.sumOf { it.duration ?: 0 },
            explicit = remote.explicit || albumPage.songs.any { it.explicit },
            description = albumPage.description ?: album.description,
            lastUpdateTime = LocalDateTime.now(),
        )
        update(entity)
        albumPage.songs.forEachIndexed { index, songItem ->
            val metadata = songItem.toMediaMetadata()
            if (getSongByIdBlocking(metadata.id) == null) insert(metadata) else upsert(metadata.toSongEntity())
            upsert(SongAlbumMap(songId = metadata.id, albumId = entity.id, index = index))
        }
        val remoteArtists = remote.artists
        if (remoteArtists != null) {
            val resolved = remoteArtists.mapIndexed { index, artist ->
                ensureArtist(MediaMetadata.Artist(artist.id, artist.name)) to index
            }
            val retained = resolved.map { it.first }.toSet()
            artists.filter { it.id !in retained }.forEach { delete(it) }
            deleteAlbumArtistMaps(entity.id)
            resolved.forEach { (artistId, index) ->
                insert(AlbumArtistMap(albumId = entity.id, artistId = artistId, order = index))
            }
        }
    }

    @Transaction
    suspend fun update(playlistEntity: PlaylistEntity, playlistItem: PlaylistItem) {
        update(
            playlistEntity.copy(
                name = playlistItem.title,
                browseId = playlistItem.id,
                thumbnailUrl = playlistItem.thumbnail,
                isEditable = playlistItem.isEditable,
                remoteSongCount = playlistItem.songCountText
                    ?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() },
                playEndpointParams = playlistItem.playEndpoint?.params,
                shuffleEndpointParams = playlistItem.shuffleEndpoint?.params,
                radioEndpointParams = playlistItem.radioEndpoint?.params,
            ),
        )
    }

    private suspend fun ensureArtist(artist: MediaMetadata.Artist): String {
        val metadataId = artist.id
        if (metadataId != null) {
            if (getArtistById(metadataId) == null) {
                insert(ArtistEntity(id = metadataId, name = artist.name, channelId = metadataId))
            }
            return metadataId
        }
        artistByName(artist.name)?.let { return it.id }
        val generated = ArtistEntity.generateArtistId()
        insert(ArtistEntity(id = generated, name = artist.name))
        return generated
    }

    // ------------------------------------------------------------------
    // Maintenance
    // ------------------------------------------------------------------

    @RawQuery
    fun raw(query: SupportSQLiteQuery): Int

    @Query("PRAGMA wal_checkpoint(FULL)")
    fun checkpoint()

    @Transaction
    @Query("SELECT * FROM song WHERE isLocal = 1 ORDER BY title COLLATE NOCASE ASC, id ASC")
    fun localSongs(): Flow<List<Song>>

    @Query("SELECT id FROM song WHERE isLocal = 1")
    suspend fun localSongIds(): List<String>

    @Query("DELETE FROM song WHERE isLocal = 1")
    suspend fun clearLocalSongs()

    @Query(
        "DELETE FROM album WHERE isLocal = 1 AND id NOT IN " +
            "(SELECT albumId FROM song WHERE isLocal = 1 AND albumId IS NOT NULL)",
    )
    suspend fun pruneLocalAlbums()

    @Query(
        "DELETE FROM artist WHERE isLocal = 1 AND id NOT IN " +
            "(SELECT song_artist_map.artistId FROM song_artist_map JOIN song " +
            "ON song.id = song_artist_map.songId WHERE song.isLocal = 1)",
    )
    suspend fun pruneLocalArtists()

    @Query("SELECT * FROM artist WHERE id IN (:ids)")
    suspend fun getArtistEntitiesByIds(ids: List<String>): List<ArtistEntity>

    @Query("SELECT * FROM album WHERE id IN (:ids)")
    suspend fun getAlbumEntitiesByIds(ids: List<String>): List<AlbumEntity>

    @Query("DELETE FROM song_album_map WHERE songId = :songId")
    suspend fun deleteSongAlbumMaps(songId: String)

    @Query("DELETE FROM format WHERE id NOT IN (SELECT id FROM song)")
    suspend fun pruneFormats()

    @Query("DELETE FROM playCount WHERE song NOT IN (SELECT id FROM song)")
    suspend fun prunePlayCounts()

    @Query("DELETE FROM song WHERE id IN (:songIds)")
    suspend fun deleteSongsByIds(songIds: List<String>)

    @Query("DELETE FROM song_artist_map WHERE songId = :songId")
    suspend fun deleteSongArtistMaps(songId: String)

    @Query("DELETE FROM album_artist_map WHERE albumId IN (:albumIds)")
    suspend fun deleteAlbumArtistMapsByAlbumIds(albumIds: List<String>)

    @Query("DELETE FROM album_artist_map WHERE albumId = :albumId")
    suspend fun deleteAlbumArtistMaps(albumId: String)
}
