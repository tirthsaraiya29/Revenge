package com.revenge.music.listentogether

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.revenge.core.constants.ListenTogetherSyncVolumeKey
import com.revenge.core.extensions.metadata
import com.revenge.core.extensions.toMediaItem
import com.revenge.core.models.MediaMetadata
import com.revenge.music.playback.PlayerConnection
import com.revenge.core.utils.dataStore
import com.revenge.core.utils.get
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Base64
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

class ListenTogetherManager(
  private val client: ListenTogetherClient,
  @ApplicationContext private val context: Context,
) {
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
    }.stateIn(scope, SharingStarted.Eagerly, false)

  private val _chatMessages = MutableStateFlow<List<ChatMessagePayload>>(emptyList())
  val chatMessages: StateFlow<List<ChatMessagePayload>> = _chatMessages.asStateFlow()

  @Volatile private var playerConnection: PlayerConnection? = null
  @Volatile private var isSyncing: Boolean = false
  @Volatile private var previousMuteState: Boolean? = null
  @Volatile private var lastActionKey: String? = null
  @Volatile private var lastActionAt: Long = 0L
  @Volatile private var pendingSync: PlaybackActionPayload? = null
  @Volatile private var pendingBufferTrackId: String? = null
  private var initialized = false

  fun setPlayerConnection(connection: PlayerConnection?) {
    val previous = playerConnection
    playerConnection = connection
    if (connection == null) {
      restoreMute(previous)
      return
    }
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

  fun requestSync() = client.requestSync()

  fun clearLogs() = client.clearLogs()

  fun suggestTrack(track: TrackInfo) = client.suggestTrack(track)

  fun approveSuggestion(suggestionId: String) = client.approveSuggestion(suggestionId)

  fun rejectSuggestion(suggestionId: String, reason: String? = null) =
    client.rejectSuggestion(suggestionId, reason)

  fun forceReconnect() = client.forceReconnect()

  fun getPersistedRoomCode(): String? = client.getPersistedRoomCode()

  fun getSessionAge(): Long = client.getSessionAge()

  fun sendTrackChange(metadata: MediaMetadata) {
    if (!canControlMusic || isSyncing) return
    val track =
      TrackInfo(
        id = metadata.id,
        title = metadata.title,
        artist = metadata.artists.joinToString(", ") { it.name },
        album = metadata.album?.title,
        duration = if (metadata.duration > 0) metadata.duration.toLong() * 1000 else 180_000L,
        thumbnail = metadata.thumbnailUrl,
        suggestedBy = metadata.suggestedBy,
      )
    client.sendPlaybackAction(
      PlaybackActionPayload(
        action = PlaybackAction.CHANGE_TRACK,
        trackId = metadata.id,
        trackInfo = track,
        serverTime = System.currentTimeMillis(),
      ),
    )
  }

  fun sendChatMessage(message: String, replyTo: RepliedMessage? = null) {
    val marker =
      replyTo?.let { reply ->
        val encoded =
          Base64.getEncoder()
            .encodeToString("${reply.username}|${reply.message}".toByteArray(Charsets.UTF_8))
        "​[RPLY:$encoded]​"
      }.orEmpty()
    client.sendChat(ChatPayload(message = marker + message))
  }

  // ------------------------------------------------------------------ events

  private fun onEvent(event: ListenTogetherEvent) {
    when (event) {
      is ListenTogetherEvent.PlaybackSync -> applyPlayback(event.action)
      is ListenTogetherEvent.BufferWait -> {
        pendingBufferTrackId = event.trackId
        pendingSync = null
      }
      is ListenTogetherEvent.BufferComplete -> {
        if (pendingBufferTrackId == event.trackId) {
          pendingBufferTrackId = null
          pendingSync?.let { applyPlayback(it) }
          pendingSync = null
        }
      }
      is ListenTogetherEvent.SyncStateReceived -> applySyncState(event.state)
      is ListenTogetherEvent.ChatMessageReceived -> {
        val (visible, reply) = parseReply(event.payload.message)
        _chatMessages.value =
          (_chatMessages.value + event.payload.copy(message = visible, replyTo = reply)).takeLast(500)
      }
      is ListenTogetherEvent.LocalSuggestionApproved -> {
        event.payload.trackInfo.toMediaMetadata()?.let { metadata ->
          playerConnection?.player?.addMediaItem(metadata.toMediaItem())
        }
      }
      is ListenTogetherEvent.Kicked, is ListenTogetherEvent.Disconnected -> {
        _chatMessages.value = emptyList()
        restoreMute(playerConnection)
      }
      else -> Unit
    }
  }

  private fun applyPlayback(payload: PlaybackActionPayload) {
    if (pendingBufferTrackId != null) {
      pendingSync = payload
      return
    }
    val connection = playerConnection ?: return
    val now = System.currentTimeMillis()
    val desiredPosition =
      (payload.position ?: 0L) + max(0L, now - (payload.serverTime ?: now))
    val key =
      "${payload.action}:${payload.trackId}:${payload.position}:${payload.serverTime}"
    if (key == lastActionKey && now - lastActionAt <= 1_000) return
    lastActionKey = key
    lastActionAt = now

    isSyncing = true
    try {
      when (payload.action) {
        PlaybackAction.PLAY -> {
          val drift = abs(connection.player.currentPosition - desiredPosition)
          if (drift > 2_000 && desiredPosition > 0) connection.seekTo(desiredPosition)
          if (!connection.isPlaying.value) connection.play()
        }
        PlaybackAction.PAUSE -> {
          if (connection.isPlaying.value) connection.pause()
        }
        PlaybackAction.SEEK -> {
          val tolerance = if (connection.isPlaying.value) 3_000 else 2_000
          if (abs(connection.player.currentPosition - desiredPosition) > tolerance) {
            connection.seekTo(desiredPosition)
          }
        }
        PlaybackAction.SKIP_NEXT -> connection.seekToNext()
        PlaybackAction.SKIP_PREV -> connection.seekToPrevious()
        PlaybackAction.CHANGE_TRACK -> {
          payload.trackInfo?.toMediaMetadata()?.let { metadata ->
            val player = connection.player
            val index =
              (0 until player.mediaItemCount).firstOrNull {
                player.getMediaItemAt(it).mediaId == metadata.id
              }
            if (index != null) {
              player.seekTo(index, 0L)
            } else {
              player.addMediaItem(metadata.toMediaItem())
              player.seekTo(player.mediaItemCount - 1, 0L)
            }
          }
        }
        PlaybackAction.QUEUE_ADD -> {
          payload.trackInfo?.toMediaMetadata()?.let { metadata ->
            val player = connection.player
            val item = metadata.toMediaItem()
            if (payload.insertNext) {
              player.addMediaItem(player.currentMediaItemIndex + 1, item)
            } else {
              player.addMediaItem(item)
            }
          }
        }
        PlaybackAction.QUEUE_REMOVE -> {
          val player = connection.player
          val index =
            (0 until player.mediaItemCount).firstOrNull {
              player.getMediaItemAt(it).mediaId == payload.trackId
            }
          if (index != null) player.removeMediaItem(index)
        }
        PlaybackAction.QUEUE_CLEAR -> connection.player.clearMediaItems()
        PlaybackAction.SYNC_QUEUE -> {
          payload.queue.forEach { track ->
            track.toMediaMetadata()?.let { connection.player.addMediaItem(it.toMediaItem()) }
          }
        }
        PlaybackAction.SET_VOLUME -> {
          if (context.dataStore[ListenTogetherSyncVolumeKey] ?: true) {
            payload.volume?.let { connection.player.volume = it.coerceIn(0f, 1f) }
          }
        }
      }
    } catch (e: Exception) {
      Timber.tag("ListenTogether").w(e, "Playback sync failed")
    } finally {
      isSyncing = false
    }
  }

  private fun applySyncState(state: SyncStatePayload) {
    val connection = playerConnection ?: return
    isSyncing = true
    try {
      val desired =
        (state.position ?: 0L) + max(0L, System.currentTimeMillis() - state.lastUpdate)
      val player = connection.player
      state.currentTrack?.toMediaMetadata()?.let { metadata ->
        if (player.currentMediaItem?.mediaId != metadata.id) {
          player.addMediaItem(metadata.toMediaItem())
          player.seekTo(player.mediaItemCount - 1, 0L)
        }
      }
      if (desired > 0) connection.seekTo(desired)
      if (state.isPlaying) connection.play() else connection.pause()
      state.volume?.let { player.volume = it.coerceIn(0f, 1f) }
    } finally {
      isSyncing = false
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
      }.getOrNull() ?: return text to null
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
