package com.revenge.music.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.revenge.core.constants.LastFullSyncKey
import com.revenge.core.db.MusicDatabase
import com.revenge.core.db.entities.PlaylistSongMap
import com.revenge.core.db.entities.SongEntity
import com.revenge.core.models.MediaMetadata
import com.revenge.core.utils.dataStore
import com.revenge.innertube.YouTube
import com.revenge.innertube.models.SongItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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

@Singleton
class SyncUtils
@Inject
constructor(
  private val database: MusicDatabase,
  @ApplicationContext private val context: Context,
) {
  companion object {
    const val SYNC_COOLDOWN = 1800L
    private const val RETRY_ATTEMPTS = 3
    private const val INTER_OPERATION_DELAY_MS = 50L
    private val LAST_FULL_SYNC_STORE_KEY = longPreferencesKey("last_full_sync_epoch")
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val mutex = Mutex()
  private val _syncState = MutableStateFlow(SyncState())
  val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
  private var activeJob: Job? = null

  fun isLoggedIn(): Boolean = YouTube.cookie?.contains("SAPISID") == true

  fun performFullSync() = enqueue(SyncOperation.FullSync)

  suspend fun fullSyncSuspend() = process(SyncOperation.FullSync)

  fun performLikedSongsSync() = enqueue(SyncOperation.LikedSongs)

  suspend fun likedSongsSyncSuspend() = process(SyncOperation.LikedSongs)

  fun performLibrarySongsSync() = enqueue(SyncOperation.LibrarySongs)

  suspend fun librarySongsSyncSuspend() = process(SyncOperation.LibrarySongs)

  fun performUploadedSongsSync() = enqueue(SyncOperation.UploadedSongs)

  suspend fun uploadedSongsSyncSuspend() = process(SyncOperation.UploadedSongs)

  fun performLikedAlbumsSync() = enqueue(SyncOperation.LikedAlbums)

  suspend fun likedAlbumsSyncSuspend() = process(SyncOperation.LikedAlbums)

  fun performUploadedAlbumsSync() = enqueue(SyncOperation.UploadedAlbums)

  suspend fun uploadedAlbumsSyncSuspend() = process(SyncOperation.UploadedAlbums)

  fun performArtistsSubscriptionsSync() = enqueue(SyncOperation.ArtistsSubscriptions)

  suspend fun artistsSubscriptionsSyncSuspend() = process(SyncOperation.ArtistsSubscriptions)

  fun performSavedPlaylistsSync() = enqueue(SyncOperation.SavedPlaylists)

  suspend fun savedPlaylistsSyncSuspend() = process(SyncOperation.SavedPlaylists)

  fun performAutoSyncPlaylistsSync() = enqueue(SyncOperation.AutoSyncPlaylists)

  suspend fun autoSyncPlaylistsSyncSuspend() = process(SyncOperation.AutoSyncPlaylists)

  fun performSinglePlaylistSync(browseId: String, playlistId: String) =
    enqueue(SyncOperation.SinglePlaylist(browseId, playlistId))

  fun likeSong(song: SongEntity) = enqueue(SyncOperation.LikeSong(song))

  fun runAllSyncs() = enqueue(SyncOperation.FullSync)

  fun cancelAllSyncs() {
    activeJob?.cancel()
    activeJob = null
  }

  /** Drops every row that only exists because of a YouTube account sync. */
  suspend fun clearAllSyncedContent() = mutex.withLock {
    withContext(Dispatchers.IO) {
      database.withTransaction {
        database.clearSyncedPlaylists()
        database.clearSetVideoIds()
      }
    }
  }

  fun tryAutoSync() {
    scope.launch {
      if (!isLoggedIn()) return@launch
      if (!NetworkUtils.isInternetAvailable(context)) return@launch
      val last =
        runCatching {
          context.dataStore.data.first()[LastFullSyncKey]
        }
          .getOrNull() ?: 0L
      if (System.currentTimeMillis() / 1000 - last < SYNC_COOLDOWN) return@launch
      process(SyncOperation.FullSync)
    }
  }

  private fun enqueue(operation: SyncOperation) {
    activeJob = scope.launch { process(operation) }
  }

  private suspend fun process(operation: SyncOperation) = mutex.withLock {
    if (!isLoggedIn()) return@withLock
    withContext(Dispatchers.IO) {
      _syncState.value =
        _syncState.value.copy(
          overallStatus = SyncStatus.Syncing,
          currentOperation = operation,
        )
      try {
        when (operation) {
          SyncOperation.FullSync -> runFullSync()
          SyncOperation.LikedSongs -> runSection(operation) {}
          SyncOperation.LibrarySongs -> runSection(operation) {}
          SyncOperation.UploadedSongs -> runSection(operation) {}
          SyncOperation.LikedAlbums -> runSection(operation) {}
          SyncOperation.UploadedAlbums -> runSection(operation) {}
          SyncOperation.ArtistsSubscriptions -> runSection(operation) {}
          SyncOperation.SavedPlaylists -> runSection(operation) {}
          SyncOperation.AutoSyncPlaylists -> runSection(operation) {}
          is SyncOperation.SinglePlaylist ->
            runSection(operation) { syncPlaylist(operation.browseId, operation.playlistId) }
          is SyncOperation.LikeSong ->
            runSection(operation) {
              runCatching { YouTube.likeVideo(operation.song.id, true) }
            }
          SyncOperation.CleanupDuplicates -> runCatching { cleanupDuplicates() }
          SyncOperation.ClearAllSynced -> {
            database.withTransaction {
              database.clearSyncedPlaylists()
              database.clearSetVideoIds()
            }
          }
        }
        _syncState.value =
          _syncState.value.copy(
            overallStatus = SyncStatus.Completed,
            currentOperation = null,
          )
      } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
      } catch (t: Throwable) {
        Timber.e(t, "Sync failed")
        _syncState.value =
          _syncState.value.copy(
            overallStatus = SyncStatus.Error(t.message.orEmpty()),
            currentOperation = null,
          )
      }
    }
  }

  private suspend fun runFullSync() {
    val operations =
      listOf(
        SyncOperation.LikedSongs,
        SyncOperation.LibrarySongs,
        SyncOperation.UploadedSongs,
        SyncOperation.LikedAlbums,
        SyncOperation.UploadedAlbums,
        SyncOperation.ArtistsSubscriptions,
        SyncOperation.SavedPlaylists,
        SyncOperation.AutoSyncPlaylists,
      )
    for (operation in operations) {
      try {
        runSection(operation) {}
      } catch (t: Throwable) {
        Timber.e(t, "Operation $operation failed; continuing")
      }
      delay(INTER_OPERATION_DELAY_MS)
    }
    runCatching {
      context.dataStore.edit { preferences ->
        preferences[LastFullSyncKey] = System.currentTimeMillis() / 1000
      }
    }
  }

  private suspend fun runSection(
    operation: SyncOperation,
    action: suspend () -> Unit,
  ) {
    _syncState.value = sectionState(operation, SyncStatus.Syncing)
    try {
      withRetry { action() }
      _syncState.value = sectionState(operation, SyncStatus.Completed)
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
      throw cancelled
    } catch (t: Throwable) {
      _syncState.value = sectionState(operation, SyncStatus.Error(t.message.orEmpty()))
      throw t
    }
  }

  private fun sectionState(operation: SyncOperation, status: SyncStatus): SyncState {
    val current = _syncState.value.copy(currentOperation = operation)
    return when (operation) {
      SyncOperation.LikedSongs -> current.copy(likedSongs = status)
      SyncOperation.LibrarySongs -> current.copy(librarySongs = status)
      SyncOperation.UploadedSongs -> current.copy(uploadedSongs = status)
      SyncOperation.LikedAlbums -> current.copy(likedAlbums = status)
      SyncOperation.UploadedAlbums -> current.copy(uploadedAlbums = status)
      SyncOperation.ArtistsSubscriptions -> current.copy(artists = status)
      SyncOperation.SavedPlaylists,
      SyncOperation.AutoSyncPlaylists,
      is SyncOperation.SinglePlaylist -> current.copy(playlists = status)
      else -> current
    }
  }

  private suspend fun <T> withRetry(block: suspend () -> T): T {
    var attempt = 0
    var lastError: Throwable? = null
    while (attempt < RETRY_ATTEMPTS) {
      try {
        return block()
      } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
      } catch (t: Throwable) {
        lastError = t
        attempt++
        if (attempt < RETRY_ATTEMPTS) delay(1_000L shl (attempt - 1))
      }
    }
    throw lastError ?: IllegalStateException("Sync failed")
  }

  private suspend fun syncPlaylist(browseId: String, playlistId: String) {
    val remote = runCatching { YouTube.playlist(browseId).getOrNull() }.getOrNull() ?: return
    val remoteSongs = remote.songs
    if (remoteSongs.isEmpty()) return
    val remoteIds = remoteSongs.map { it.id }.toSet()
    val local = runCatching { database.playlistSongs(playlistId).first() }.getOrDefault(emptyList())
    val localIds = local.map { it.song.song.id }.toSet()
    val localMissingRemotely = localIds.any { it !in remoteIds }

    database.withTransaction {
      if (localMissingRemotely) {
        var position = (local.maxOfOrNull { it.map.position } ?: -1) + 1
        remoteSongs
          .filterNot { it.id in localIds }
          .forEach { song ->
            database.insert(song.toSyncMediaMetadata())
            database.insert(
              PlaylistSongMap(
                playlistId = playlistId,
                songId = song.id,
                position = position++,
              ),
            )
          }
      } else {
        database.clearPlaylist(playlistId)
        remoteSongs.forEachIndexed { index, song ->
          database.insert(song.toSyncMediaMetadata())
          database.insert(
            PlaylistSongMap(
              playlistId = playlistId,
              songId = song.id,
              position = index,
            ),
          )
        }
      }
    }
  }

  private suspend fun cleanupDuplicates() {
    val playlists = runCatching { database.playlistsByNameAsc().first() }.getOrDefault(emptyList())
    playlists
      .groupBy { it.playlist.browseId }
      .forEach { (browseId, group) ->
        if (browseId == null || group.size <= 1) return@forEach
        val keep = group.maxByOrNull { it.songCount } ?: return@forEach
        group
          .filterNot { it.playlist.id == keep.playlist.id }
          .forEach { duplicate ->
            runCatching { database.delete(duplicate.playlist) }
          }
      }
  }
}

private fun SongItem.toSyncMediaMetadata(): MediaMetadata =
  MediaMetadata(
    id = id,
    title = title,
    artists = artists.map { MediaMetadata.Artist(it.id, it.name) },
    duration = duration ?: -1,
    thumbnailUrl = thumbnail,
    album = album?.let { MediaMetadata.Album(it.id, it.name) },
    musicVideoType = musicVideoType,
    explicit = explicit,
  )
