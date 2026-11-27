package com.revenge.music.listentogether

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.revenge.core.constants.ListenTogetherSmartResyncKey
import com.revenge.core.constants.ListenTogetherSyncVolumeKey
import com.revenge.core.extensions.metadata
import com.revenge.core.extensions.toMediaItem
import com.revenge.core.models.MediaMetadata
import com.revenge.core.utils.dataStore
import com.revenge.core.utils.get
import com.revenge.innertube.YouTube
import com.revenge.music.playback.PlayerConnection
import com.revenge.playback.queues.ListQueue
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Base64
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

class ListenTogetherManager(
  private val client: ListenTogetherClient,
  @ApplicationContext private val context: Context,
) {
  private companion object {
    const val SENT_SUPPRESSION_MS = 300L
    const val APPLIED_SUPPRESSION_MS = 200L
    const val POSITION_TOLERANCE_MS = 2_000L
    const val PLAYBACK_POSITION_TOLERANCE_MS = 3_000L
    const val HEARTBEAT_MS = 10_000L
    const val QUEUE_DEBOUNCE_MS = 500L
    const val PLAYER_READY_WAIT_MS = 5_000L
    const val QUEUE_OBSERVER_TICK_MS = 100L
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  val connectionState = client.connectionState
  val roomState = client.roomState
  val role = client.role
  val userId = client.userId
  val pendingJoinRequests = client.pendingJoinRequests
  val bufferingUsers = client.bufferingUsers
  val logs = client.logs
  val events = client.events
  val blockedUsernames = client.blockedUsernames
  val pendingSuggestions = client.pendingSuggestions
  val rtt = client.rtt

  val isInRoom: Boolean
    get() = client.isInRoom

  val isHost: Boolean
    get() = client.isHost

  val hasPersistedSession: Boolean
    get() = client.hasPersistedSession

  val allowParticipantControl: Boolean
    get() = client.roomState.value?.allowParticipantControl == true

  val canControlMusic: Boolean
    get() = !isInRoom || isHost || allowParticipantControl

  val isGuestPlaybackRestricted: Boolean
    get() = isInRoom && !isHost && !allowParticipantControl

  val guestPlaybackRestricted: StateFlow<Boolean> =
    combine(client.roomState, client.role) { state, currentRole ->
        state != null && currentRole != RoomRole.HOST && !state.allowParticipantControl
      }
      .stateIn(scope, SharingStarted.Eagerly, false)

  private val _chatMessages = MutableStateFlow<List<ChatMessagePayload>>(emptyList())
  val chatMessages: StateFlow<List<ChatMessagePayload>> = _chatMessages.asStateFlow()

  @Volatile private var playerConnection: PlayerConnection? = null
  @Volatile private var attachedPlayer: Player? = null
  @Volatile private var isSyncing: Boolean = false
  @Volatile private var previousMuteState: Boolean? = null
  @Volatile private var lastActionKey: String? = null
  @Volatile private var lastActionAt: Long = 0L
  @Volatile private var pendingSync: PlaybackActionPayload? = null
  @Volatile private var pendingBufferTrackId: String? = null
  @Volatile private var lastReportedTrackId: String? = null
  @Volatile private var lastReportedPlaying: Boolean = false
  @Volatile private var syncGeneration: Long = 0L
  private val suppression = SuppressionWindow(SENT_SUPPRESSION_MS, APPLIED_SUPPRESSION_MS)
  private val observerJobs = mutableListOf<Job>()
  private var initialized = false

  private val playerListener =
    object : Player.Listener {
      override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val connection = playerConnection ?: return
        val mediaId = mediaItem?.mediaId ?: return
        handleTrackChange(connection, mediaId)
      }

      override fun onIsPlayingChanged(isPlaying: Boolean) {
        val connection = playerConnection ?: return
        handlePlayPause(connection, isPlaying)
      }

      override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
      ) {
        if (reason != Player.DISCONTINUITY_REASON_SEEK) return
        val connection = playerConnection ?: return
        handleSeek(connection, newPosition.positionMs)
      }
    }

  fun setPlayerConnection(connection: PlayerConnection?) {
    val previous = playerConnection
    if (previous === connection) {
      if (connection != null && isGuestPlaybackRestricted) applyMute(true)
      return
    }
    detachPlayer()
    playerConnection = connection
    if (connection == null) {
      restoreMute(previous)
      return
    }
    attachPlayer(connection)
    if (isGuestPlaybackRestricted) applyMute(true)
  }

  fun initialize() {
    if (initialized) return
    initialized = true
    client.initialize()
    scope.launch { client.events.collect { event -> onEvent(event) } }
    scope.launch {
      client.roomState.collect { state ->
        if (state == null) {
          restoreMute(playerConnection)
        } else if (isGuestPlaybackRestricted) {
          applyMute(true)
        } else {
          restoreMute(playerConnection)
        }
      }
    }
  }

  fun connect() {
    initialize()
    client.connect()
  }

  fun disconnect() {
    client.disconnect()
    cleanupTransientState()
    restoreMute(playerConnection)
  }

  fun createRoom(username: String) {
    client.createRoom(username)
  }

  fun joinRoom(roomCode: String, username: String) {
    client.joinRoom(roomCode, username)
  }

  fun leaveRoom() {
    client.leaveRoom()
    cleanupTransientState()
    restoreMute(playerConnection)
  }

  fun approveJoin(userId: String) = client.approveJoin(userId)

  fun rejectJoin(userId: String, reason: String? = null) = client.rejectJoin(userId, reason)

  fun kickUser(userId: String, reason: String? = null) = client.kickUser(userId, reason)

  fun blockUser(username: String) = client.blockUser(username)

  fun unblockUser(username: String) = client.unblockUser(username)

  fun getBlockedUsernames(): Set<String> = client.getBlockedUsernames()

  fun transferHost(newHostId: String) = client.transferHost(newHostId)

  fun updateRoomSettings(allowParticipantControl: Boolean) =
    client.updateRoomSettings(allowParticipantControl)

  fun requestSync() {
    if (!isGuestPlaybackRestricted) return
    client.requestSync()
  }

  fun clearLogs() = client.clearLogs()

  fun suggestTrack(track: TrackInfo) = client.suggestTrack(track)

  fun approveSuggestion(suggestionId: String) = client.approveSuggestion(suggestionId)

  fun rejectSuggestion(suggestionId: String, reason: String? = null) =
    client.rejectSuggestion(suggestionId, reason)

  fun forceReconnect() = client.forceReconnect()

  fun getPersistedRoomCode(): String? = client.getPersistedRoomCode()

  fun getSessionAge(): Long = client.getSessionAge()

  fun sendTrackChange(metadata: MediaMetadata) {
    val connection = playerConnection ?: return
    if (!canControlMusic || isSyncing) return
    sendChangeTrack(connection, metadata, metadata.id)
    lastReportedTrackId = metadata.id
  }

  fun sendChatMessage(message: String, replyTo: RepliedMessage? = null) {
    val marker =
      replyTo
        ?.let { reply ->
          val encoded =
            Base64.getEncoder()
              .encodeToString("${reply.username}|${reply.message}".toByteArray(Charsets.UTF_8))
          "​[RPLY:$encoded]​"
        }
        .orEmpty()
    client.sendChat(ChatPayload(message = marker + message))
  }

  // ------------------------------------------------------------------ player attachment

  private fun attachPlayer(connection: PlayerConnection) {
    val player = runCatching { connection.player }.getOrNull() ?: return
    attachedPlayer = player
    player.addListener(playerListener)
    lastReportedTrackId = player.currentMediaItem?.mediaId ?: connection.mediaMetadata.value?.id
    lastReportedPlaying = connection.isPlaying.value
    observerJobs += scope.launch { observeQueue(connection) }
    observerJobs += scope.launch { observeVolume(connection) }
    observerJobs += scope.launch { heartbeat() }
  }

  private fun detachPlayer() {
    observerJobs.forEach { it.cancel() }
    observerJobs.clear()
    runCatching { attachedPlayer?.removeListener(playerListener) }
    attachedPlayer = null
  }

  // ------------------------------------------------------------------ outbound player events

  private fun mayReport(connection: PlayerConnection): Boolean =
    playerConnection === connection &&
      isInRoom &&
      canControlMusic &&
      !isSyncing &&
      !suppression.isSuppressed(System.currentTimeMillis())

  private fun handleTrackChange(connection: PlayerConnection, mediaId: String) {
    if (mediaId == lastReportedTrackId) return
    if (!mayReport(connection)) return
    val metadata = connection.mediaMetadata.value?.takeIf { it.id == mediaId }
    sendChangeTrack(connection, metadata, mediaId)
    lastReportedTrackId = mediaId
    if (connection.isPlaying.value) sendPlay(connection.player.currentPosition)
    lastReportedPlaying = connection.isPlaying.value
  }

  private fun handlePlayPause(connection: PlayerConnection, isPlaying: Boolean) {
    if (!mayReport(connection)) return
    val player = connection.player
    val position = player.currentPosition
    val currentId = player.currentMediaItem?.mediaId
    if (currentId != null && currentId != lastReportedTrackId) {
      sendChangeTrack(
        connection,
        connection.mediaMetadata.value?.takeIf { it.id == currentId },
        currentId,
      )
      lastReportedTrackId = currentId
    }
    if (isPlaying) {
      sendPlay(position)
      lastReportedPlaying = true
    } else if (lastReportedPlaying) {
      sendPause(position)
      lastReportedPlaying = false
    }
  }

  private fun handleSeek(connection: PlayerConnection, position: Long) {
    if (!mayReport(connection)) return
    sendAction(PlaybackActionPayload(action = PlaybackAction.SEEK, position = position))
  }

  private fun sendChangeTrack(
    connection: PlayerConnection,
    metadata: MediaMetadata?,
    mediaId: String,
  ) {
    val track =
      metadata?.toTrackInfo()
        ?: TrackInfo(
          id = mediaId.ifBlank { "unknown" },
          title = "Unknown",
          artist = "Unknown",
          album = "",
          duration = 0L,
          thumbnail = "",
        )
    sendAction(
      PlaybackActionPayload(
        action = PlaybackAction.CHANGE_TRACK,
        trackId = track.id,
        trackInfo = track,
        queue = currentQueue(connection),
        queueTitle = connection.queueTitle.value,
        serverTime = System.currentTimeMillis(),
      ),
    )
  }

  private fun sendPlay(position: Long) {
    sendAction(
      PlaybackActionPayload(
        action = PlaybackAction.PLAY,
        position = position,
        serverTime = System.currentTimeMillis(),
      ),
    )
  }

  private fun sendPause(position: Long) {
    sendAction(PlaybackActionPayload(action = PlaybackAction.PAUSE, position = position))
  }

  private fun sendSyncQueue(connection: PlayerConnection, windows: List<Timeline.Window>) {
    val queue = windows.map { it.toTrackInfo() }
    if (queue.isEmpty()) return
    sendAction(
      PlaybackActionPayload(
        action = PlaybackAction.SYNC_QUEUE,
        queue = queue,
        queueTitle = connection.queueTitle.value,
      ),
    )
  }

  private fun sendAction(payload: PlaybackActionPayload): Boolean {
    val sent = client.sendPlaybackAction(payload)
    if (sent) suppression.markSent(System.currentTimeMillis())
    return sent
  }

  private fun sendCurrentState() {
    val connection = playerConnection ?: return
    if (!canControlMusic) return
    val mediaId = connection.player.currentMediaItem?.mediaId ?: return
    val metadata = connection.mediaMetadata.value?.takeIf { it.id == mediaId }
    sendChangeTrack(connection, metadata, mediaId)
    lastReportedTrackId = mediaId
    if (connection.isPlaying.value) sendPlay(connection.player.currentPosition)
    lastReportedPlaying = connection.isPlaying.value
  }

  private fun currentQueue(connection: PlayerConnection): List<TrackInfo> =
    connection.queueWindows.value.map { it.toTrackInfo() }

  private fun Timeline.Window.toTrackInfo(): TrackInfo =
    mediaItem.metadata?.toTrackInfo()
      ?: TrackInfo(
        id = "unknown",
        title = "Unknown",
        artist = "Unknown",
        album = "",
        duration = 0L,
        thumbnail = "",
      )

  private fun MediaMetadata.toTrackInfo(): TrackInfo =
    TrackInfo(
      id = id,
      title = title,
      artist = artists.joinToString(", ") { it.name },
      album = album?.title,
      duration = if (duration > 0) duration.toLong() * 1000 else 180_000L,
      thumbnail = thumbnailUrl,
      suggestedBy = suggestedBy,
    )

  // ------------------------------------------------------------------ observers

  private suspend fun observeQueue(connection: PlayerConnection) {
    val debouncer = ChangeDebouncer<List<Timeline.Window>>(QUEUE_DEBOUNCE_MS)
    debouncer.seed(connection.queueWindows.value)
    while (true) {
      if (playerConnection !== connection) return
      val now = System.currentTimeMillis()
      debouncer.offer(connection.queueWindows.value, now)
      if (mayReport(connection)) {
        debouncer.ready(now)?.let { windows -> sendSyncQueue(connection, windows) }
      }
      delay(QUEUE_OBSERVER_TICK_MS)
    }
  }

  private suspend fun observeVolume(connection: PlayerConnection) {
    var last = connection.service.playerVolume.value
    connection.service.playerVolume.collect { volume ->
      if (playerConnection !== connection) return@collect
      if (!isHost || !isInRoom || context.dataStore[ListenTogetherSyncVolumeKey] != true) {
        last = volume
        return@collect
      }
      if (abs(volume - last) < 0.01f) {
        last = volume
        return@collect
      }
      last = volume
      sendAction(
        PlaybackActionPayload(
          action = PlaybackAction.SET_VOLUME,
          volume = volume.coerceIn(0f, 1f),
        ),
      )
    }
  }

  private suspend fun heartbeat() {
    while (true) {
      delay(HEARTBEAT_MS)
      val connection = playerConnection ?: continue
      if (playerConnection !== connection) continue
      if (!isHost || !isInRoom) continue
      if (!connection.isPlaying.value) continue
      if (connection.player.playbackState != Player.STATE_READY) continue
      sendPlay(connection.player.currentPosition)
    }
  }

  // ------------------------------------------------------------------ events

  private fun onEvent(event: ListenTogetherEvent) {
    when (event) {
      is ListenTogetherEvent.RoomCreated -> {
        if (isHost) sendCurrentState()
      }
      is ListenTogetherEvent.JoinApproved ->
        event.state?.let { state ->
          scope.launch { applySyncState(state.toSyncState()) }
        }
      is ListenTogetherEvent.UserJoined -> {
        if (isHost) sendCurrentState()
      }
      is ListenTogetherEvent.HostChanged -> {
        if (isHost) sendCurrentState()
      }
      is ListenTogetherEvent.PlaybackSync -> scope.launch { applyPlayback(event.action) }
      is ListenTogetherEvent.BufferComplete -> {
        if (pendingBufferTrackId == event.trackId) {
          pendingBufferTrackId = null
          val pending = pendingSync
          pendingSync = null
          pending?.let { payload -> scope.launch { applyPlayback(payload, bypassBuffer = true) } }
        }
      }
      is ListenTogetherEvent.SyncStateReceived -> scope.launch { applySyncState(event.state) }
      is ListenTogetherEvent.Reconnected -> {
        if (isHost) {
          sendCurrentState()
        } else if (isGuestPlaybackRestricted && event.state != null) {
          scope.launch { applySyncState(event.state.toSyncState()) }
          scope.launch {
            if (context.dataStore[ListenTogetherSmartResyncKey] ?: true) {
              delay(1_000)
              requestSync()
            }
          }
        }
      }
      is ListenTogetherEvent.ChatMessageReceived -> {
        val (visible, reply) = parseReply(event.payload.message)
        _chatMessages.value =
          (_chatMessages.value + event.payload.copy(message = visible, replyTo = reply)).takeLast(
            500,
          )
      }
      is ListenTogetherEvent.LocalSuggestionApproved -> {
        event.payload.trackInfo.toMediaMetadata()?.let { metadata ->
          playerConnection?.playNext(metadata.toMediaItem())
        }
      }
      is ListenTogetherEvent.Kicked,
      is ListenTogetherEvent.Disconnected,
      is ListenTogetherEvent.ConnectionError -> {
        cleanupTransientState()
        restoreMute(playerConnection)
      }
      else -> Unit
    }
  }

  private fun RoomState.toSyncState(): SyncStatePayload =
    SyncStatePayload(
      currentTrack = currentTrack,
      isPlaying = isPlaying,
      position = position,
      lastUpdate = lastUpdate,
      queue = queue.takeIf { it.isNotEmpty() },
      volume = volume,
    )

  private fun cleanupTransientState() {
    _chatMessages.value = emptyList()
    pendingSync = null
    pendingBufferTrackId = null
    lastReportedTrackId = null
    lastReportedPlaying = false
  }

  // ------------------------------------------------------------------ guest sync

  private suspend fun applyPlayback(payload: PlaybackActionPayload, bypassBuffer: Boolean = false) {
    val connection = playerConnection ?: return
    if (!isGuestPlaybackRestricted) return
    if (pendingBufferTrackId != null && !bypassBuffer) {
      pendingSync = payload
      return
    }
    val now = System.currentTimeMillis()
    val desiredPosition = (payload.position ?: 0L) + max(0L, now - (payload.serverTime ?: now))
    val key = "${payload.action}:${payload.trackId}:${payload.position}:${payload.serverTime}"
    if (!bypassBuffer && key == lastActionKey && now - lastActionAt <= 1_000) return
    lastActionKey = key
    lastActionAt = now

    beginSync()
    try {
      awaitPlayerReady(connection)
      if (playerConnection !== connection) return
      val player = connection.player
      when (payload.action) {
        PlaybackAction.PLAY -> {
          val tolerance =
            if (connection.isPlaying.value) PLAYBACK_POSITION_TOLERANCE_MS
            else POSITION_TOLERANCE_MS
          if (abs(player.currentPosition - desiredPosition) > tolerance && desiredPosition > 0) {
            connection.seekTo(desiredPosition)
          }
          if (!connection.isPlaying.value) connection.play()
        }
        PlaybackAction.PAUSE -> {
          if (connection.isPlaying.value) connection.pause()
          if (
            abs(player.currentPosition - desiredPosition) > POSITION_TOLERANCE_MS &&
              desiredPosition > 0
          ) {
            connection.seekTo(desiredPosition)
          }
        }
        PlaybackAction.SEEK -> {
          if (abs(player.currentPosition - desiredPosition) > POSITION_TOLERANCE_MS) {
            connection.seekTo(desiredPosition)
          }
        }
        PlaybackAction.CHANGE_TRACK ->
          applyTrack(connection, payload, desiredPosition, bypassBuffer)
        PlaybackAction.SKIP_NEXT -> connection.seekToNext()
        PlaybackAction.SKIP_PREV -> connection.seekToPrevious()
        PlaybackAction.QUEUE_ADD -> applyQueueAdd(connection, payload)
        PlaybackAction.QUEUE_REMOVE -> removeAfterCurrent(connection, payload.trackId)
        PlaybackAction.QUEUE_CLEAR -> clearAfterCurrent(connection)
        PlaybackAction.SET_VOLUME -> {
          if (!isHost && (context.dataStore[ListenTogetherSyncVolumeKey] ?: true)) {
            payload.volume?.let {
              connection.service.playerVolume.value = it.coerceIn(0f, 1f)
            }
          }
        }
        PlaybackAction.SYNC_QUEUE -> applySyncQueue(connection, payload, desiredPosition)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Timber.tag("ListenTogether").w(e, "Playback sync failed")
    } finally {
      endSync()
    }
  }

  private suspend fun applyTrack(
    connection: PlayerConnection,
    payload: PlaybackActionPayload,
    position: Long,
    bypassBuffer: Boolean,
  ) {
    val track = payload.trackInfo ?: return
    val queueItems = payload.queue.mapNotNull { it.toMediaMetadata()?.toMediaItem() }
    if (queueItems.isNotEmpty()) {
      val index = queueItems.indexOfFirst { it.mediaId == track.id }.coerceAtLeast(0)
      connection.service.playQueue(
        ListQueue(payload.queueTitle ?: "Listen Together", queueItems, index, position),
        playWhenReady = false,
      )
    } else {
      val loaded =
        withContext(Dispatchers.IO) { YouTube.queue(listOf(track.id)).getOrNull() }
          .orEmpty()
          .map { it.toMediaItem() }
      val items = loaded.ifEmpty { listOfNotNull(track.toMediaMetadata()?.toMediaItem()) }
      if (items.isEmpty()) return
      connection.service.playQueue(
        ListQueue("Listen Together", items, 0, position),
        playWhenReady = false,
      )
    }
    if (bypassBuffer) {
      if (position > 0) connection.seekTo(position)
      connection.pause()
    } else {
      pendingSync = payload
      pendingBufferTrackId = track.id
      client.sendBufferReady(track.id)
    }
  }

  private suspend fun applyQueueAdd(
    connection: PlayerConnection,
    payload: PlaybackActionPayload,
  ) {
    val track = payload.trackInfo ?: return
    val item =
      withContext(Dispatchers.IO) {
        YouTube.queue(listOf(track.id)).getOrNull()?.firstOrNull()?.toMediaItem()
      } ?: track.toMediaMetadata()?.toMediaItem() ?: return
    connection.allowInternalSync = true
    try {
      if (payload.insertNext) connection.playNext(item) else connection.addToQueue(item)
    } finally {
      connection.allowInternalSync = false
    }
  }

  private fun removeAfterCurrent(connection: PlayerConnection, trackId: String?) {
    if (trackId.isNullOrEmpty()) return
    val player = connection.player
    val current = player.currentMediaItemIndex
    for (index in current + 1 until player.mediaItemCount) {
      if (player.getMediaItemAt(index).mediaId == trackId) {
        player.removeMediaItem(index)
        return
      }
    }
  }

  private fun clearAfterCurrent(connection: PlayerConnection) {
    val player = connection.player
    for (index in player.mediaItemCount - 1 downTo player.currentMediaItemIndex + 1) {
      player.removeMediaItem(index)
    }
  }

  private fun applySyncQueue(
    connection: PlayerConnection,
    payload: PlaybackActionPayload,
    position: Long,
  ) {
    val items = payload.queue.mapNotNull { it.toMediaMetadata()?.toMediaItem() }
    if (items.isEmpty()) return
    val currentId = connection.player.currentMediaItem?.mediaId
    val index = items.indexOfFirst { it.mediaId == currentId }.coerceAtLeast(0)
    connection.service.playQueue(
      ListQueue(payload.queueTitle ?: "Listen Together", items, index, position),
      playWhenReady = connection.isPlaying.value,
    )
  }

  private suspend fun applySyncState(state: SyncStatePayload) {
    val connection = playerConnection ?: return
    if (!isGuestPlaybackRestricted) return
    beginSync()
    try {
      awaitPlayerReady(connection)
      if (playerConnection !== connection) return
      val desired =
        (state.position ?: 0L) +
          if (state.isPlaying) {
            max(0L, System.currentTimeMillis() - state.lastUpdate)
          } else {
            0L
          }
      val track = state.currentTrack
      if (track != null) {
        val queue = state.queue.orEmpty().mapNotNull { it.toMediaMetadata()?.toMediaItem() }
        val items = queue.ifEmpty { listOfNotNull(track.toMediaMetadata()?.toMediaItem()) }
        if (items.isNotEmpty()) {
          val index = items.indexOfFirst { it.mediaId == track.id }.coerceAtLeast(0)
          connection.service.playQueue(
            ListQueue("Listen Together", items, index, desired),
            playWhenReady = state.isPlaying,
          )
        }
      }
      if (desired > 0 && connection.player.currentMediaItem != null) {
        connection.seekTo(desired)
      }
      if (state.isPlaying) connection.play() else connection.pause()
      state.volume?.let { connection.service.playerVolume.value = it.coerceIn(0f, 1f) }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Timber.tag("ListenTogether").w(e, "Sync state apply failed")
    } finally {
      endSync()
    }
  }

  private suspend fun awaitPlayerReady(connection: PlayerConnection) {
    withTimeoutOrNull(PLAYER_READY_WAIT_MS) {
      connection.service.isPlayerReady.first { it }
    }
  }

  private fun beginSync() {
    isSyncing = true
    suppression.markApplied(System.currentTimeMillis())
  }

  private fun endSync() {
    suppression.markApplied(System.currentTimeMillis())
    val generation = ++syncGeneration
    scope.launch {
      delay(APPLIED_SUPPRESSION_MS)
      if (syncGeneration == generation) isSyncing = false
    }
  }

  private fun TrackInfo.toMediaMetadata(): MediaMetadata? {
    if (id.isBlank()) return null
    return MediaMetadata(
      id = id,
      title = title,
      artists = listOf(MediaMetadata.Artist(null, artist.ifBlank { "Unknown Artist" })),
      duration = (duration / 1000).toInt(),
      thumbnailUrl = thumbnail,
      album = album?.let { MediaMetadata.Album("", it) },
      suggestedBy = suggestedBy,
    )
  }

  private fun parseReply(text: String): Pair<String, RepliedMessage?> {
    val regex = Regex("^\\u200B\\[RPLY:([A-Za-z0-9+/=]+)]\\u200B(.*)$", RegexOption.DOT_MATCHES_ALL)
    val match = regex.matchEntire(text) ?: return text to null
    val decoded =
      runCatching {
        String(Base64.getDecoder().decode(match.groupValues[1]), Charsets.UTF_8)
      }
        .getOrNull() ?: return text to null
    val separator = decoded.indexOf('|')
    if (separator <= 0) return text to null
    return match.groupValues[2] to
      RepliedMessage(decoded.substring(0, separator), decoded.substring(separator + 1))
  }

  private fun applyMute(muted: Boolean) {
    val connection = playerConnection ?: return
    if (previousMuteState == null) previousMuteState = connection.isMuted.value
    connection.setMuted(muted)
  }

  private fun restoreMute(connection: PlayerConnection?) {
    val previous = previousMuteState ?: return
    previousMuteState = null
    connection?.setMuted(previous)
  }
}
