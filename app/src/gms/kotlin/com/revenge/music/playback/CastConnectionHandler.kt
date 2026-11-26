package com.revenge.music.playback

import android.content.Context
import androidx.media3.common.MediaItem
import com.google.android.gms.cast.CastDevice
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.Session
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.revenge.music.extensions.metadata
import com.revenge.music.models.MediaMetadata as AppMediaMetadata
import com.revenge.music.playback.MusicService
import com.revenge.music.ui.utils.resize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber

/** GMS-only bridge between the local player and a Cast receiver session. */
class CastConnectionHandler(
  private val context: Context,
  private val scope: CoroutineScope,
  private val musicService: MusicService,
) {
  private val _isCasting = MutableStateFlow(false)
  val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()

  private val _isConnecting = MutableStateFlow(false)
  val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

  private val _castDeviceName = MutableStateFlow<String?>(null)
  val castDeviceName: StateFlow<String?> = _castDeviceName.asStateFlow()

  private val _deviceType = MutableStateFlow(CastDeviceKind.UNKNOWN)
  val deviceType: StateFlow<CastDeviceKind> = _deviceType.asStateFlow()

  private val _castPosition = MutableStateFlow(0L)
  val castPosition: StateFlow<Long> = _castPosition.asStateFlow()

  private val _castDuration = MutableStateFlow(0L)
  val castDuration: StateFlow<Long> = _castDuration.asStateFlow()

  private val _castIsPlaying = MutableStateFlow(false)
  val castIsPlaying: StateFlow<Boolean> = _castIsPlaying.asStateFlow()

  private val _castIsBuffering = MutableStateFlow(false)
  val castIsBuffering: StateFlow<Boolean> = _castIsBuffering.asStateFlow()

  private val _castVolume = MutableStateFlow(1f)
  val castVolume: StateFlow<Float> = _castVolume.asStateFlow()

  private val _autoReconnecting = MutableStateFlow(false)
  val autoReconnecting: StateFlow<Boolean> = _autoReconnecting.asStateFlow()

  @Volatile var isSyncingFromCast: Boolean = false
    private set

  private var pollJob: Job? = null
  private var castContext: CastContext? = null
  private var registered = false
  private val castQueueIds = mutableListOf<String>()

  private val sessionListener =
    object : SessionManagerListener<CastSession> {
      override fun onSessionStarted(session: CastSession, sessionId: String) {
        onSessionConnected(session)
      }

      override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
        onSessionConnected(session)
      }

      override fun onSessionSuspended(session: CastSession, reason: Int) {
        _isConnecting.value = true
      }

      override fun onSessionEnded(session: CastSession, error: Int) {
        clearCastState()
      }

      override fun onSessionStarting(session: CastSession) {
        _isConnecting.value = true
      }

      override fun onSessionStartFailed(session: CastSession, error: Int) {
        _isConnecting.value = false
        _autoReconnecting.value = true
      }

      override fun onSessionEnding(session: CastSession) {
        clearCastState()
      }

      override fun onSessionResuming(session: CastSession, sessionId: String) {
        _isConnecting.value = true
      }

      override fun onSessionResumeFailed(session: CastSession, error: Int) {
        _isConnecting.value = false
        _autoReconnecting.value = true
      }
    }

  fun initialize(): Boolean =
    try {
      castContext = CastContext.getSharedInstance(context)
      if (!registered) {
        castContext?.sessionManager?.addSessionManagerListener(sessionListener, CastSession::class.java)
        registered = true
      }
      true
    } catch (e: Throwable) {
      Timber.tag(TAG).w(e, "Cast unavailable")
      false
    }

  fun isCastAvailable(): Boolean =
    try {
      CastContext.getSharedInstance(context) != null
    } catch (e: Throwable) {
      false
    }

  fun disconnect() {
    runCatching { castContext?.sessionManager?.endCurrentSession(true) }
    clearCastState()
  }

  fun release() {
    stopPolling()
    runCatching {
      if (registered) {
        castContext?.sessionManager?.removeSessionManagerListener(
          sessionListener,
          CastSession::class.java,
        )
      }
      castContext?.sessionManager?.endCurrentSession(true)
    }
    registered = false
    castContext = null
    clearCastState()
  }

  fun loadCurrentMedia() {
    val item = musicService.player.currentMediaItem ?: return
    item.metadata?.let(::loadMedia)
  }

  fun loadMedia(metadata: AppMediaMetadata) {
    scope.launch { loadMediaWithQueue(metadata) }
  }

  private suspend fun loadMediaWithQueue(metadata: AppMediaMetadata) {
    val client = remoteClient() ?: return
    val player = musicService.player
    val currentIndex = player.currentMediaItemIndex
    val start = (currentIndex - 2).coerceAtLeast(0)
    val end = (currentIndex + 2).coerceAtMost(player.mediaItemCount - 1)
    if (player.mediaItemCount == 0 || end < start) return
    val items = mutableListOf<MediaQueueItem>()
    val ids = mutableListOf<String>()
    var startIndex = 0
    for (index in start..end) {
      val item = player.getMediaItemAt(index)
      val itemMetadata = if (index == currentIndex) metadata else item.metadata
      if (itemMetadata == null) continue
      val info = buildMediaInfo(itemMetadata) ?: continue
      items += MediaQueueItem.Builder(info).build()
      ids += itemMetadata.id
      if (index == currentIndex) startIndex = items.lastIndex
    }
    if (items.isEmpty()) return
    castQueueIds.clear()
    castQueueIds += ids

    var lastError: Throwable? = null
    for (attempt in 0..2) {
      try {
        val startPosition =
          if (metadata.id == player.currentMediaItem?.mediaId) player.currentPosition else 0L
        val result =
          client.queueLoad(items.toTypedArray(), startIndex, 0, startPosition, null)
        if (result != null) {
          _isCasting.value = true
          _autoReconnecting.value = false
          runCatching { player.pause() }
          return
        }
      } catch (e: Throwable) {
        lastError = e
      }
      delay(1_000L * attempt)
    }
    _castIsBuffering.value = false
    _autoReconnecting.value = true
    Timber.tag(TAG).w(lastError, "Cast queue load failed")
  }

  suspend fun insertItemsAfterCurrent(items: List<MediaItem>) {
    val client = remoteClient() ?: return
    val queueItems =
      items.mapNotNull { item ->
        val metadata = item.metadata ?: return@mapNotNull null
        buildMediaInfo(metadata)?.let { MediaQueueItem.Builder(it).build() }
      }
    if (queueItems.isEmpty()) return
    val ids = runCatching { client.mediaQueue.itemIds }.getOrDefault(intArrayOf())
    val currentItemId = client.currentItem?.itemId ?: MediaQueueItem.INVALID_ITEM_ID
    val currentIndex = ids.indexOf(currentItemId)
    val beforeId =
      if (currentIndex >= 0 && currentIndex + 1 < ids.size) ids[currentIndex + 1]
      else MediaQueueItem.INVALID_ITEM_ID
    runCatching { client.queueInsertItems(queueItems.toTypedArray(), beforeId, null) }
  }

  suspend fun appendItemsToCastQueue(items: List<MediaItem>) {
    val client = remoteClient() ?: return
    val queueItems =
      items.mapNotNull { item ->
        val metadata = item.metadata ?: return@mapNotNull null
        buildMediaInfo(metadata)?.let { MediaQueueItem.Builder(it).build() }
      }
    if (queueItems.isEmpty()) return
    runCatching { client.queueAppendItem(queueItems.first(), null) }
    queueItems.drop(1).forEach { runCatching { client.queueAppendItem(it, null) } }
  }

  fun play() {
    runCatching { remoteClient()?.play() }
  }

  fun pause() {
    runCatching { remoteClient()?.pause() }
  }

  fun seekTo(position: Long) {
    runCatching { remoteClient()?.seek(position.coerceAtLeast(0)) }
    _castPosition.value = position.coerceAtLeast(0)
  }

  fun setVolume(volume: Float) {
    val clamped = volume.coerceIn(0f, 1f)
    val session = castContext?.sessionManager?.currentCastSession ?: return
    runCatching { session.volume = clamped.toDouble() }
  }

  fun navigateToMediaIfInQueue(mediaId: String): Boolean {
    if (mediaId !in castQueueIds) return false
    val player = musicService.player
    val localIndex =
      (0 until player.mediaItemCount).firstOrNull {
        player.getMediaItemAt(it).mediaId == mediaId
      } ?: return false
    player.seekTo(localIndex, 0L)
    return true
  }

  fun skipToNext() {
    runCatching { remoteClient()?.queueNext(null) }
  }

  fun skipToPrevious() {
    runCatching { remoteClient()?.queuePrev(null) }
  }

  fun removeItemFromQueue(itemId: Int) {
    runCatching { remoteClient()?.queueRemoveItem(itemId, null) }
  }

  fun moveItemInQueue(itemId: Int, newIndex: Int) {
    val client = remoteClient() ?: return
    val ids = runCatching { client.mediaQueue.itemIds }.getOrDefault(intArrayOf())
    if (itemId !in ids) return
    val before = ids.getOrNull(newIndex) ?: MediaQueueItem.INVALID_ITEM_ID
    runCatching { client.queueReorderItems(intArrayOf(itemId), before, null) }
  }

  fun clearQueue() {
    val client = remoteClient() ?: return
    val ids = runCatching { client.mediaQueue.itemIds }.getOrDefault(intArrayOf())
    runCatching { client.queueRemoveItems(ids, null) }
    castQueueIds.clear()
  }

  // ------------------------------------------------------------------ internals

  private suspend fun buildMediaInfo(metadata: AppMediaMetadata): MediaInfo? {
    val artists = metadata.artists.joinToString(", ") { it.name }
    val streamUrl = musicService.getStreamUrl(metadata.id, metadata.title, artists) ?: return null
    val castMetadata =
      com.google.android.gms.cast.MediaMetadata(com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_MUSIC_TRACK)
        .apply {
          putString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE, metadata.title)
          putString(com.google.android.gms.cast.MediaMetadata.KEY_ARTIST, artists)
          metadata.album?.title?.let {
            putString(com.google.android.gms.cast.MediaMetadata.KEY_ALBUM_TITLE, it)
          }
          metadata.thumbnailUrl?.let { thumbnail ->
            addImage(
              com.google.android.gms.common.images.WebImage(
                android.net.Uri.parse(thumbnail.resize(1080, 1080)),
              ),
            )
          }
        }
    return MediaInfo.Builder(streamUrl)
      .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
      .setContentType("audio/mp4")
      .setMetadata(castMetadata)
      .setCustomData(JSONObject().put("mediaId", metadata.id))
      .build()
  }

  private fun onSessionConnected(session: CastSession) {
    _isCasting.value = true
    _isConnecting.value = false
    _autoReconnecting.value = false
    val device = session.castDevice
    _castDeviceName.value = device?.friendlyName
    _deviceType.value =
      CastDeviceKind.fromName(
        device?.friendlyName.orEmpty(),
        device?.modelName,
      )
    startPolling()
  }

  private fun clearCastState() {
    stopPolling()
    _isCasting.value = false
    _isConnecting.value = false
    _castDeviceName.value = null
    _deviceType.value = CastDeviceKind.UNKNOWN
    _castPosition.value = 0
    _castDuration.value = 0
    _castIsPlaying.value = false
    _castIsBuffering.value = false
    _autoReconnecting.value = false
    castQueueIds.clear()
  }

  private fun startPolling() {
    if (pollJob?.isActive == true) return
    pollJob =
      scope.launch {
        while (isActive && _isCasting.value) {
          refreshStatus()
          delay(1_000)
        }
      }
  }

  private fun stopPolling() {
    pollJob?.cancel()
    pollJob = null
  }

  private fun refreshStatus() {
    val client = remoteClient() ?: return
    val session = castContext?.sessionManager?.currentCastSession
    _castPosition.value = client.approximateStreamPosition.coerceAtLeast(0)
    _castDuration.value = client.streamDuration.coerceAtLeast(0)
    _castIsPlaying.value = client.isPlaying
    _castIsBuffering.value =
      client.mediaStatus?.playerState ==
        com.google.android.gms.cast.MediaStatus.PLAYER_STATE_BUFFERING
    session?.volume?.let { _castVolume.value = it.toFloat().coerceIn(0f, 1f) }
  }

  private fun remoteClient(): RemoteMediaClient? =
    runCatching {
      castContext?.sessionManager?.currentCastSession?.remoteMediaClient
    }.getOrNull()

  private companion object {
    const val TAG = "CastConnectionHandler"
  }
}
