package com.revenge.music.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.revenge.core.constants.LastFullSyncKey
import com.revenge.core.constants.SYNC_COOLDOWN
import com.revenge.core.db.MusicDatabase
import com.revenge.core.db.entities.AlbumEntity
import com.revenge.core.db.entities.ArtistEntity
import com.revenge.core.db.entities.PlaylistEntity
import com.revenge.core.db.entities.PlaylistSongMap
import com.revenge.core.db.entities.SongEntity
import com.revenge.core.extensions.isUserLoggedIn
import com.revenge.core.extensions.isSyncEnabled
import com.revenge.core.models.toMediaMetadata
import com.revenge.core.models.toSongEntity
import com.revenge.core.playback.ISyncUtils
import com.revenge.core.utils.dataStore
import com.revenge.core.utils.get
import com.revenge.innertube.YouTube
import com.revenge.innertube.models.AlbumItem
import com.revenge.innertube.models.ArtistItem
import com.revenge.innertube.models.PlaylistItem
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.models.YTItem
import com.revenge.innertube.pages.LibraryPage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

sealed class SyncOperation {
    data object FullSync : SyncOperation()
    data object LikedSongs : SyncOperation()
    data object LibrarySongs : SyncOperation()
    data object UploadedSongs : SyncOperation()
    data object LikedAlbums : SyncOperation()
    data object UploadedAlbums : SyncOperation()
    data object ArtistsSubscriptions : SyncOperation()
    data object SavedPlaylists : SyncOperation()
    data object AutoSyncPlaylists : SyncOperation()
    data class SinglePlaylist(val browseId: String, val playlistId: String) : SyncOperation()
    data class LikeSong(val song: SongEntity) : SyncOperation()
    data object CleanupDuplicates : SyncOperation()
    data object ClearAllSynced : SyncOperation()
}

sealed class SyncStatus {
    data object Idle : SyncStatus()
    data object Syncing : SyncStatus()
    data class Error(val message: String) : SyncStatus()
    data object Completed : SyncStatus()
}

data class SyncState(
    val overallStatus: SyncStatus = SyncStatus.Idle,
    val likedSongs: SyncStatus = SyncStatus.Idle,
    val librarySongs: SyncStatus = SyncStatus.Idle,
    val uploadedSongs: SyncStatus = SyncStatus.Idle,
    val likedAlbums: SyncStatus = SyncStatus.Idle,
    val uploadedAlbums: SyncStatus = SyncStatus.Idle,
    val artists: SyncStatus = SyncStatus.Idle,
    val playlists: SyncStatus = SyncStatus.Idle,
    val currentOperation: SyncOperation? = null,
)

/**
 * Serial, retrying synchronisation of YouTube Music library content into the local database.
 * Operations are no-ops while signed out; one failure never stops the queue.
 */
@Singleton
class SyncUtils @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) : ISyncUtils {

    private val mutex = Mutex()

    @Volatile
    private var scope = newScope()

    @Volatile
    private var queue = Channel<SyncOperation>(Channel.UNLIMITED)

    @Volatile
    private var consumer: Job? = null

    private val mutableSyncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = mutableSyncState.asStateFlow()

    init {
        startConsumer()
    }

    // ---- public fire-and-forget surface ----------------------------------------------------

    fun syncLikedSongs() = enqueue(SyncOperation.LikedSongs)
    fun syncLibrarySongs() = enqueue(SyncOperation.LibrarySongs)
    fun syncUploadedSongs() = enqueue(SyncOperation.UploadedSongs)
    fun syncLikedAlbums() = enqueue(SyncOperation.LikedAlbums)
    fun syncUploadedAlbums() = enqueue(SyncOperation.UploadedAlbums)
    fun syncArtistsSubscriptions() = enqueue(SyncOperation.ArtistsSubscriptions)
    fun syncArtists() = enqueue(SyncOperation.ArtistsSubscriptions)
    fun syncSavedPlaylists() = enqueue(SyncOperation.SavedPlaylists)
    fun syncPlaylists() = enqueue(SyncOperation.SavedPlaylists)
    fun syncAutoSyncPlaylists() = enqueue(SyncOperation.AutoSyncPlaylists)
    fun syncPlaylist(browseId: String, playlistId: String) =
        enqueue(SyncOperation.SinglePlaylist(browseId, playlistId))

    fun performLikedSongsSync() = syncLikedSongs()
    fun performLibrarySongsSync() = syncLibrarySongs()
    fun performUploadedSongsSync() = syncUploadedSongs()
    fun performLikedAlbumsSync() = syncLikedAlbums()
    fun performUploadedAlbumsSync() = syncUploadedAlbums()
    fun performArtistsSync() = syncArtistsSubscriptions()
    fun performArtistsSubscriptionsSync() = syncArtistsSubscriptions()
    fun performSavedPlaylistsSync() = syncSavedPlaylists()
    fun performAutoSyncPlaylistsSync() = syncAutoSyncPlaylists()
    fun performSinglePlaylistSync(browseId: String, playlistId: String) =
        syncPlaylist(browseId, playlistId)

    fun runAllSyncs() = enqueue(SyncOperation.FullSync)
    fun performFullSync() = runAllSyncs()
    fun cleanupDuplicates() = enqueue(SyncOperation.CleanupDuplicates)
    fun clearAllSynced() = enqueue(SyncOperation.ClearAllSynced)

    override fun likeSong(song: SongEntity) = enqueue(SyncOperation.LikeSong(song))

    fun tryAutoSync() {
        scope.launch {
            if (!context.isUserLoggedIn() || !context.isSyncEnabled()) return@launch
            val last = context.dataStore[LastFullSyncKey] ?: 0L
            val now = System.currentTimeMillis() / 1000L
            if (now - last < SYNC_COOLDOWN) return@launch
            perform(SyncOperation.FullSync)
        }
    }

    fun cancelAllSyncs() {
        consumer?.cancel()
        consumer = null
        scope.cancel()
        queue = Channel(Channel.UNLIMITED)
        scope = newScope()
        startConsumer()
    }

    // ---- suspend surface -------------------------------------------------------------------

    suspend fun syncLikedSongsSuspend() = perform(SyncOperation.LikedSongs)
    suspend fun likedSongsSyncSuspend() = perform(SyncOperation.LikedSongs)
    suspend fun syncLibrarySongsSuspend() = perform(SyncOperation.LibrarySongs)
    suspend fun librarySongsSyncSuspend() = perform(SyncOperation.LibrarySongs)
    suspend fun syncUploadedSongsSuspend() = perform(SyncOperation.UploadedSongs)
    suspend fun uploadedSongsSyncSuspend() = perform(SyncOperation.UploadedSongs)
    suspend fun syncLikedAlbumsSuspend() = perform(SyncOperation.LikedAlbums)
    suspend fun likedAlbumsSyncSuspend() = perform(SyncOperation.LikedAlbums)
    suspend fun syncUploadedAlbumsSuspend() = perform(SyncOperation.UploadedAlbums)
    suspend fun uploadedAlbumsSyncSuspend() = perform(SyncOperation.UploadedAlbums)
    suspend fun syncArtistsSuspend() = perform(SyncOperation.ArtistsSubscriptions)
    suspend fun artistsSubscriptionsSyncSuspend() = perform(SyncOperation.ArtistsSubscriptions)
    suspend fun syncSavedPlaylistsSuspend() = perform(SyncOperation.SavedPlaylists)
    suspend fun savedPlaylistsSyncSuspend() = perform(SyncOperation.SavedPlaylists)
    suspend fun syncAutoSyncPlaylistsSuspend() = perform(SyncOperation.AutoSyncPlaylists)
    suspend fun syncPlaylistSuspend(browseId: String, playlistId: String) =
        perform(SyncOperation.SinglePlaylist(browseId, playlistId))
    suspend fun singlePlaylistSyncSuspend(browseId: String, playlistId: String) =
        perform(SyncOperation.SinglePlaylist(browseId, playlistId))
    suspend fun runAllSyncsSuspend() = perform(SyncOperation.FullSync)
    suspend fun fullSyncSuspend() = perform(SyncOperation.FullSync)
    suspend fun cleanupDuplicatesSuspend() = perform(SyncOperation.CleanupDuplicates)
    suspend fun clearAllSyncedContent() = perform(SyncOperation.ClearAllSynced)

    // ---- queue -----------------------------------------------------------------------------

    private fun newScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun startConsumer() {
        consumer = scope.launch {
            for (operation in queue) {
                runCatching { perform(operation) }
                    .onFailure { Timber.w(it, "Sync operation failed: $operation") }
                delay(INTER_OPERATION_DELAY_MS)
            }
        }
    }

    private fun enqueue(operation: SyncOperation) {
        if (operation !is SyncOperation.ClearAllSynced &&
            operation !is SyncOperation.CleanupDuplicates &&
            !context.isUserLoggedIn()
        ) {
            return
        }
        queue.trySend(operation)
    }

    private suspend fun perform(operation: SyncOperation) {
        mutex.withLock {
            if (operation !is SyncOperation.ClearAllSynced &&
                operation !is SyncOperation.CleanupDuplicates &&
                !context.isUserLoggedIn()
            ) {
                return
            }
            mutableSyncState.update { it.copy(overallStatus = SyncStatus.Syncing, currentOperation = operation) }
            try {
                execute(operation)
                mutableSyncState.update { it.copy(overallStatus = SyncStatus.Completed) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Timber.w(error, "Sync operation failed: $operation")
                mutableSyncState.update {
                    it.copy(overallStatus = SyncStatus.Error(error.message ?: "Sync failed"))
                }
            }
        }
    }

    private suspend fun execute(operation: SyncOperation) {
        when (operation) {
            SyncOperation.FullSync -> fullSync()
            SyncOperation.LikedSongs -> {
                updateSection(setLikedSongs = SyncStatus.Syncing)
                saveSongs(fetchPlaylistSongs(LIKED_SONGS_PLAYLIST_ID), liked = true)
                updateSection(setLikedSongs = SyncStatus.Completed)
            }
            SyncOperation.LibrarySongs -> {
                updateSection(setLibrarySongs = SyncStatus.Syncing)
                saveSongs(fetchLibraryItems(LIBRARY_SONGS_BROWSE_ID).filterIsInstance<SongItem>(), library = true)
                updateSection(setLibrarySongs = SyncStatus.Completed)
            }
            SyncOperation.UploadedSongs -> {
                updateSection(setUploadedSongs = SyncStatus.Syncing)
                saveSongs(fetchLibraryItems(UPLOADED_SONGS_BROWSE_ID).filterIsInstance<SongItem>(), uploaded = true)
                updateSection(setUploadedSongs = SyncStatus.Completed)
            }
            SyncOperation.LikedAlbums -> {
                updateSection(setLikedAlbums = SyncStatus.Syncing)
                saveAlbums(fetchLibraryItems(LIKED_ALBUMS_BROWSE_ID).filterIsInstance<AlbumItem>(), liked = true)
                updateSection(setLikedAlbums = SyncStatus.Completed)
            }
            SyncOperation.UploadedAlbums -> {
                updateSection(setUploadedAlbums = SyncStatus.Syncing)
                saveAlbums(fetchLibraryItems(UPLOADED_ALBUMS_BROWSE_ID).filterIsInstance<AlbumItem>(), uploaded = true)
                updateSection(setUploadedAlbums = SyncStatus.Completed)
            }
            SyncOperation.ArtistsSubscriptions -> {
                updateSection(setArtists = SyncStatus.Syncing)
                saveArtists(fetchLibraryItems(ARTIST_SUBSCRIPTIONS_BROWSE_ID).filterIsInstance<ArtistItem>())
                updateSection(setArtists = SyncStatus.Completed)
            }
            SyncOperation.SavedPlaylists -> {
                updateSection(setPlaylists = SyncStatus.Syncing)
                savePlaylists(fetchLibraryItems(SAVED_PLAYLISTS_BROWSE_ID).filterIsInstance<PlaylistItem>())
                updateSection(setPlaylists = SyncStatus.Completed)
            }
            SyncOperation.AutoSyncPlaylists -> {
                updateSection(setPlaylists = SyncStatus.Syncing)
                val autoSync = database.playlistsByNameAsc().first()
                    .filter { it.playlist.isAutoSync && it.playlist.browseId != null }
                for (playlist in autoSync) {
                    val browseId = playlist.playlist.browseId ?: continue
                    runCatching { syncPlaylistSongs(browseId, playlist.playlist.id) }
                        .onFailure { Timber.w(it, "Auto-sync playlist ${playlist.id} failed") }
                }
                updateSection(setPlaylists = SyncStatus.Completed)
            }
            is SyncOperation.SinglePlaylist -> syncPlaylistSongs(operation.browseId, operation.playlistId)
            is SyncOperation.LikeSong -> {
                if (context.isUserLoggedIn()) {
                    YouTube.likeVideo(operation.song.id, operation.song.liked)
                }
            }
            SyncOperation.CleanupDuplicates -> cleanupDuplicatesInternal()
            SyncOperation.ClearAllSynced -> clearAllSyncedInternal()
        }
    }

    private suspend fun fullSync() {
        execute(SyncOperation.LikedSongs)
        execute(SyncOperation.LibrarySongs)
        execute(SyncOperation.UploadedSongs)
        execute(SyncOperation.LikedAlbums)
        execute(SyncOperation.UploadedAlbums)
        execute(SyncOperation.ArtistsSubscriptions)
        execute(SyncOperation.SavedPlaylists)
        execute(SyncOperation.AutoSyncPlaylists)
        context.dataStore.edit { it[LastFullSyncKey] = System.currentTimeMillis() / 1000L }
    }

    // ---- remote fetching -------------------------------------------------------------------

    private suspend fun <T> retry(block: suspend () -> T): T {
        var delayMs = INITIAL_RETRY_DELAY_MS
        var last: Exception? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                return block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                last = error
                if (attempt < MAX_ATTEMPTS - 1) {
                    delay(delayMs)
                    delayMs *= 2
                }
            }
        }
        throw last ?: IllegalStateException("Sync failed")
    }

    private suspend fun fetchPlaylistSongs(playlistId: String): List<SongItem> = retry {
        val first = YouTube.playlist(playlistId).getOrThrow()
        val songs = first.songs.toMutableList()
        var continuation = first.songsContinuation
        var pages = 0
        while (continuation != null && pages < MAX_CONTINUATION_PAGES) {
            val page = YouTube.playlistContinuation(continuation).getOrThrow()
            songs += page.songs
            continuation = page.continuation
            pages++
        }
        songs
    }

    private suspend fun fetchLibraryItems(browseId: String): List<YTItem> = retry {
        val first = YouTube.library(browseId).getOrThrow()
        val items = first.items.toMutableList()
        var continuation = first.continuation
        var pages = 0
        while (continuation != null && pages < MAX_CONTINUATION_PAGES) {
            val page = YouTube.libraryContinuation(continuation).getOrThrow()
            items += page.items
            continuation = page.continuation
            pages++
        }
        items
    }

    // ---- local writes ----------------------------------------------------------------------

    private suspend fun saveSongs(
        items: List<SongItem>,
        liked: Boolean = false,
        library: Boolean = false,
        uploaded: Boolean = false,
    ) {
        if (items.isEmpty()) return
        val now = LocalDateTime.now()
        database.withTransaction {
            items.forEach { item ->
                val metadata = item.toMediaMetadata()
                val existing = getSongById(item.id)
                if (existing == null) {
                    insert(metadata) { entity ->
                        entity.copy(
                            liked = liked || entity.liked,
                            likedDate = if (liked) now else entity.likedDate,
                            inLibrary = if (library) now else entity.inLibrary,
                            isUploaded = uploaded,
                        )
                    }
                } else {
                    val base = existing.song
                    upsert(
                        base.copy(
                            title = metadata.title,
                            duration = metadata.duration,
                            thumbnailUrl = metadata.thumbnailUrl ?: base.thumbnailUrl,
                            albumId = metadata.album?.id ?: base.albumId,
                            albumName = metadata.album?.title ?: base.albumName,
                            liked = base.liked || liked,
                            likedDate = base.likedDate ?: if (liked) now else null,
                            inLibrary = base.inLibrary ?: if (library) now else null,
                            isUploaded = base.isUploaded || uploaded,
                        ),
                    )
                }
            }
        }
    }

    private suspend fun saveAlbums(
        items: List<AlbumItem>,
        liked: Boolean = false,
        uploaded: Boolean = false,
    ) {
        if (items.isEmpty()) return
        val now = LocalDateTime.now()
        database.withTransaction {
            val existingAlbums = getAlbumEntitiesByIds(items.map { it.browseId }).associateBy { it.id }
            items.forEach { item ->
                val existing = existingAlbums[item.browseId]
                if (existing == null) {
                    insert(
                        AlbumEntity(
                            id = item.browseId,
                            playlistId = item.playlistId,
                            title = item.title,
                            year = item.year,
                            thumbnailUrl = item.thumbnail,
                            songCount = existing?.songCount ?: 0,
                            duration = existing?.duration ?: 0,
                            explicit = item.explicit,
                            bookmarkedAt = if (liked) now else null,
                            likedDate = if (liked) now else null,
                            isUploaded = uploaded,
                        ),
                    )
                } else {
                    update(
                        existing.copy(
                            playlistId = item.playlistId,
                            title = item.title,
                            year = item.year,
                            thumbnailUrl = item.thumbnail ?: existing.thumbnailUrl,
                            bookmarkedAt = existing.bookmarkedAt ?: if (liked) now else null,
                            likedDate = existing.likedDate ?: if (liked) now else null,
                            isUploaded = existing.isUploaded || uploaded,
                        ),
                    )
                }
            }
        }
    }

    private suspend fun saveArtists(items: List<ArtistItem>) {
        if (items.isEmpty()) return
        val now = LocalDateTime.now()
        database.withTransaction {
            val existingArtists = getArtistEntitiesByIds(items.map { it.id }).associateBy { it.id }
            items.forEach { item ->
                val existing = existingArtists[item.id]
                if (existing == null) {
                    insert(
                        ArtistEntity(
                            id = item.id,
                            name = item.title,
                            thumbnailUrl = item.thumbnail,
                            channelId = item.channelId,
                            bookmarkedAt = now,
                        ),
                    )
                } else {
                    update(
                        existing.copy(
                            name = item.title,
                            thumbnailUrl = item.thumbnail ?: existing.thumbnailUrl,
                            channelId = item.channelId ?: existing.channelId,
                            bookmarkedAt = existing.bookmarkedAt ?: now,
                        ),
                    )
                }
            }
        }
    }

    private suspend fun savePlaylists(items: List<PlaylistItem>) {
        if (items.isEmpty()) return
        val existing = database.playlistsByNameAsc().first()
            .associateBy { it.playlist.browseId }
        database.withTransaction {
            items.forEach { item ->
                val local = existing[item.id]
                if (local == null) {
                    insert(
                        PlaylistEntity(
                            name = item.title,
                            browseId = item.id,
                            thumbnailUrl = item.thumbnail,
                            isEditable = item.isEditable,
                            remoteSongCount = parseSongCount(item.songCountText),
                        ),
                    )
                } else {
                    update(
                        local.playlist.copy(
                            name = item.title,
                            thumbnailUrl = item.thumbnail ?: local.playlist.thumbnailUrl,
                            isEditable = item.isEditable,
                            remoteSongCount = parseSongCount(item.songCountText),
                        ),
                    )
                }
            }
        }
    }

    private suspend fun syncPlaylistSongs(browseId: String, playlistId: String) {
        val remote = fetchPlaylistSongs(browseId)
        if (remote.isEmpty()) return
        saveSongs(remote)

        val remoteIds = remote.map { it.id }.toSet()
        val localIds = database.playlistSongs(playlistId).first().map { it.map.songId }.toSet()
        val missingLocally = remoteIds - localIds
        val missingRemotely = localIds - remoteIds

        database.withTransaction {
            if (missingRemotely.isNotEmpty()) {
                var position = nextPlaylistPosition(playlistId)
                missingLocally.forEach { songId ->
                    insert(PlaylistSongMap(playlistId = playlistId, songId = songId, position = position))
                    position++
                }
            } else {
                clearPlaylist(playlistId)
                remote.forEachIndexed { index, song ->
                    insert(PlaylistSongMap(playlistId = playlistId, songId = song.id, position = index))
                }
            }
        }
    }

    private suspend fun cleanupDuplicatesInternal() {
        val playlists = database.playlistsByNameAsc().first()
            .filter { it.playlist.browseId != null }
        val duplicates = playlists
            .groupBy { it.playlist.browseId }
            .filterValues { it.size > 1 }
        database.withTransaction {
            for ((_, group) in duplicates) {
                val keep = group.maxByOrNull { it.songCount } ?: continue
                group.filter { it.playlist.id != keep.playlist.id }
                    .forEach { delete(it.playlist) }
            }
        }
    }

    private suspend fun clearAllSyncedInternal() {
        database.withTransaction {
            database.likedSongsByRowIdAsc().first().forEach { liked ->
                updateLikedStatus(liked.id, false, null)
            }
            database.allSongs().first().forEach { song ->
                if (song.song.inLibrary != null) inLibrary(song.id, null)
            }
            clearSyncedPlaylists()
            clearSetVideoIds()
        }
    }

    private fun updateSection(
        setLikedSongs: SyncStatus? = null,
        setLibrarySongs: SyncStatus? = null,
        setUploadedSongs: SyncStatus? = null,
        setLikedAlbums: SyncStatus? = null,
        setUploadedAlbums: SyncStatus? = null,
        setArtists: SyncStatus? = null,
        setPlaylists: SyncStatus? = null,
    ) {
        mutableSyncState.update {
            it.copy(
                likedSongs = setLikedSongs ?: it.likedSongs,
                librarySongs = setLibrarySongs ?: it.librarySongs,
                uploadedSongs = setUploadedSongs ?: it.uploadedSongs,
                likedAlbums = setLikedAlbums ?: it.likedAlbums,
                uploadedAlbums = setUploadedAlbums ?: it.uploadedAlbums,
                artists = setArtists ?: it.artists,
                playlists = setPlaylists ?: it.playlists,
            )
        }
    }

    private fun parseSongCount(text: String?): Int? =
        text?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() }

    companion object {
        private const val LIKED_SONGS_PLAYLIST_ID = "LM"
        private const val LIBRARY_SONGS_BROWSE_ID = "FEmusic_library_corpus_track_artists"
        private const val UPLOADED_SONGS_BROWSE_ID = "FEmusic_library_privately_owned_tracks"
        private const val LIKED_ALBUMS_BROWSE_ID = "FEmusic_liked_albums"
        private const val UPLOADED_ALBUMS_BROWSE_ID = "FEmusic_library_privately_owned_releases"
        private const val ARTIST_SUBSCRIPTIONS_BROWSE_ID = "FEmusic_library_corpus_artists"
        private const val SAVED_PLAYLISTS_BROWSE_ID = "FEmusic_liked_playlists"

        private const val MAX_ATTEMPTS = 3
        private const val INITIAL_RETRY_DELAY_MS = 1_000L
        private const val INTER_OPERATION_DELAY_MS = 50L
        private const val MAX_CONTINUATION_PAGES = 100
    }
}
