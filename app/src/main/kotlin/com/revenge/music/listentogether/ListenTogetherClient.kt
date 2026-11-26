package com.revenge.music.listentogether

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.edit
import com.revenge.music.R
import com.revenge.core.constants.ListenTogetherAutoApprovalKey
import com.revenge.core.constants.ListenTogetherBlockedUsersKey
import com.revenge.core.constants.ListenTogetherIsHostKey
import com.revenge.core.constants.ListenTogetherRoomCodeKey
import com.revenge.core.constants.ListenTogetherServerUrlKey
import com.revenge.core.constants.ListenTogetherSessionTimestampKey
import com.revenge.core.constants.ListenTogetherSessionTokenKey
import com.revenge.core.constants.ListenTogetherUserIdKey
import com.revenge.core.utils.dataStore
import com.revenge.core.utils.get
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import timber.log.Timber

class ListenTogetherClient(private val context: Context) {

  private companion object {
    const val MAX_RECONNECT_ATTEMPTS = 15
    const val SESSION_TTL_MS = 10 * 60 * 1000L
    const val PING_INTERVAL_MS = 25_000L
    const val NOTIFICATION_CHANNEL = "listen_together_joins"
    const val NOTIFICATION_ID = 4901
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val codec = MessageCodec()
  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
  }
  private val httpClient =
    OkHttpClient.Builder()
      .connectTimeout(30, TimeUnit.SECONDS)
      .readTimeout(60, TimeUnit.SECONDS)
      .writeTimeout(30, TimeUnit.SECONDS)
      .pingInterval(30, TimeUnit.SECONDS)
      .build()

  private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
  val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

  private val _roomState = MutableStateFlow<RoomState?>(null)
  val roomState: StateFlow<RoomState?> = _roomState.asStateFlow()

  private val _role = MutableStateFlow(RoomRole.NONE)
  val role: StateFlow<RoomRole> = _role.asStateFlow()

  private val _userId = MutableStateFlow<String?>(null)
  val userId: StateFlow<String?> = _userId.asStateFlow()

  private val _pendingJoinRequests = MutableStateFlow<List<JoinRequestPayload>>(emptyList())
  val pendingJoinRequests: StateFlow<List<JoinRequestPayload>> = _pendingJoinRequests.asStateFlow()

  private val _bufferingUsers = MutableStateFlow<List<String>>(emptyList())
  val bufferingUsers: StateFlow<List<String>> = _bufferingUsers.asStateFlow()

  private val _pendingSuggestions = MutableStateFlow<List<SuggestionReceivedPayload>>(emptyList())
  val pendingSuggestions: StateFlow<List<SuggestionReceivedPayload>> =
    _pendingSuggestions.asStateFlow()

  private val _blockedUsernames = MutableStateFlow<Set<String>>(emptySet())
  val blockedUsernames: StateFlow<Set<String>> = _blockedUsernames.asStateFlow()

  private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
  val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

  private val _events =
    MutableSharedFlow<ListenTogetherEvent>(
      replay = 0,
      extraBufferCapacity = 64,
      onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
  val events: SharedFlow<ListenTogetherEvent> = _events.asSharedFlow()

  private val _rtt = MutableStateFlow(0L)
  val rtt: StateFlow<Long> = _rtt.asStateFlow()

  val isInRoom: Boolean
    get() = _roomState.value != null

  val isHost: Boolean
    get() = _role.value == RoomRole.HOST

  val hasPersistedSession: Boolean
    get() = sessionToken != null

  @Volatile private var socket: WebSocket? = null
  @Volatile private var explicitDisconnect = false
  @Volatile private var pingSentAt = 0L
  @Volatile private var sessionToken: String? = null
  @Volatile private var roomCode: String? = null
  @Volatile private var sessionTimestamp: Long = 0L
  @Volatile private var ownUsername: String = ""

  private var pendingAction: PendingAction? = null
  private var reconnectAttempt = 0
  private var reconnectJob: Job? = null
  private var pingJob: Job? = null
  private var wakeLock: PowerManager.WakeLock? = null
  private var networkCallback: ConnectivityManager.NetworkCallback? = null

  private sealed interface PendingAction {
    data class Create(val username: String) : PendingAction
    data class Join(val roomCode: String, val username: String) : PendingAction
  }

  init {
    ListenTogetherActionReceiver.client = this
  }

  fun initialize() {
    scope.launch {
      restoreSession()
      restoreBlockedUsers()
    }
  }

  fun getPersistedRoomCode(): String? = roomCode

  fun getSessionAge(): Long =
    if (sessionTimestamp <= 0) Long.MAX_VALUE else System.currentTimeMillis() - sessionTimestamp

  // ------------------------------------------------------------------ lifecycle

  fun connect() {
    if (socket != null || _connectionState.value == ConnectionState.CONNECTING) return
    explicitDisconnect = false
    _connectionState.value = ConnectionState.CONNECTING
    addLog(LogLevel.INFO, "Connecting")
    val url = context.dataStore[ListenTogetherServerUrlKey] ?: ListenTogetherServers.defaultServerUrl
    val request = Request.Builder().url(url).build()
    socket = httpClient.newWebSocket(request, listener)
  }

  fun disconnect() {
    explicitDisconnect = true
    reconnectJob?.cancel()
    pingJob?.cancel()
    socket?.close(1000, "Client disconnect")
    socket = null
    clearSessionState()
    _connectionState.value = ConnectionState.DISCONNECTED
    releaseWakeLock()
    emit(ListenTogetherEvent.Disconnected)
  }

  fun forceReconnect() {
    if (explicitDisconnect) explicitDisconnect = false
    reconnectAttempt = 0
    socket?.cancel()
    socket = null
    _connectionState.value = ConnectionState.DISCONNECTED
    connect()
  }

  // ------------------------------------------------------------------ rooms

  fun createRoom(username: String) {
    ownUsername = username
    pendingAction = PendingAction.Create(username)
    clearPersistedSession()
    if (socket == null) connect() else sendPending()
  }

  fun joinRoom(roomCode: String, username: String) {
    ownUsername = username
    val code = roomCode.trim().uppercase(Locale.US)
    pendingAction = PendingAction.Join(code, username)
    clearPersistedSession()
    if (socket == null) connect() else sendPending()
  }

  fun leaveRoom() {
    send(MessageType.LEAVE_ROOM)
    clearSessionState()
    clearPersistedSession()
    releaseWakeLock()
  }

  fun approveJoin(userId: String) {
    send(MessageType.APPROVE_JOIN, json.encodeToString(ApproveJoinPayload.serializer(), ApproveJoinPayload(userId)))
    _pendingJoinRequests.update { list -> list.filterNot { it.userId == userId } }
    cancelJoinNotification()
  }

  fun rejectJoin(userId: String, reason: String? = null) {
    send(
      MessageType.REJECT_JOIN,
      json.encodeToString(RejectJoinPayload.serializer(), RejectJoinPayload(userId, reason)),
    )
    _pendingJoinRequests.update { list -> list.filterNot { it.userId == userId } }
    cancelJoinNotification()
  }

  fun kickUser(userId: String, reason: String? = null) {
    send(
      MessageType.KICK_USER,
      json.encodeToString(KickUserPayload.serializer(), KickUserPayload(userId, reason)),
    )
  }

  fun transferHost(newHostId: String) {
    send(
      MessageType.TRANSFER_HOST,
      json.encodeToString(TransferHostPayload.serializer(), TransferHostPayload(newHostId)),
    )
  }

  fun updateRoomSettings(allowParticipantControl: Boolean) {
    send(
      MessageType.UPDATE_ROOM_SETTINGS,
      json.encodeToString(
        UpdateRoomSettingsPayload.serializer(),
        UpdateRoomSettingsPayload(allowParticipantControl),
      ),
    )
    _roomState.update { it?.copy(allowParticipantControl = allowParticipantControl) }
  }

  fun sendPlaybackAction(payload: PlaybackActionPayload): Boolean {
    val state = _roomState.value ?: return false
    val mayControl = _role.value == RoomRole.HOST || state.allowParticipantControl
    if (!mayControl) return false
    return send(
      MessageType.PLAYBACK_ACTION,
      json.encodeToString(PlaybackActionPayload.serializer(), payload),
    )
  }

  fun sendBufferReady(trackId: String) {
    send(
      MessageType.BUFFER_READY,
      json.encodeToString(BufferReadyPayload.serializer(), BufferReadyPayload(trackId)),
    )
  }

  fun requestSync() {
    send(MessageType.REQUEST_SYNC)
  }

  fun suggestTrack(track: TrackInfo) {
    send(
      MessageType.SUGGEST_TRACK,
      json.encodeToString(SuggestTrackPayload.serializer(), SuggestTrackPayload(track)),
    )
  }

  fun approveSuggestion(suggestionId: String) {
    send(
      MessageType.APPROVE_SUGGESTION,
      json.encodeToString(
        ApproveSuggestionPayload.serializer(),
        ApproveSuggestionPayload(suggestionId),
      ),
    )
  }

  fun rejectSuggestion(suggestionId: String, reason: String? = null) {
    send(
      MessageType.REJECT_SUGGESTION,
      json.encodeToString(
        RejectSuggestionPayload.serializer(),
        RejectSuggestionPayload(suggestionId, reason),
      ),
    )
  }

  fun sendChat(payload: ChatPayload) {
    send(MessageType.CHAT, json.encodeToString(ChatPayload.serializer(), payload))
  }

  fun blockUser(username: String) {
    _blockedUsernames.update { it + username }
    persistBlockedUsers()
  }

  fun unblockUser(username: String) {
    _blockedUsernames.update { it - username }
    persistBlockedUsers()
  }

  fun getBlockedUsernames(): Set<String> = _blockedUsernames.value

  fun clearLogs() {
    _logs.value = emptyList()
  }

  // ------------------------------------------------------------------ socket events

  private val listener =
    object : WebSocketListener() {
      override fun onOpen(webSocket: WebSocket, response: Response) {
        scope.launch {
          _connectionState.value = ConnectionState.CONNECTED
          reconnectAttempt = 0
          addLog(LogLevel.INFO, "Connected")
          if (sessionToken != null && roomCode != null) {
            send(
              MessageType.RECONNECT,
              json.encodeToString(
                ReconnectPayload.serializer(),
                ReconnectPayload(sessionToken.orEmpty()),
              ),
            )
          } else if (pendingAction != null) {
            sendPending()
          }
          emit(ListenTogetherEvent.Connected(_userId.value.orEmpty()))
          startPing()
        }
      }

      override fun onMessage(webSocket: WebSocket, text: String) {
        handleFrame(text.toByteArray(Charsets.UTF_8))
      }

      override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        val frame = bytes.toByteArray()
        if (codec.shouldUpgradeToProtobuf(frame) && !codec.useProtobuf) {
          codec.useProtobuf = true
          codec.compressionEnabled = true
          addLog(LogLevel.INFO, "Upgraded to Protobuf transport")
        }
        handleFrame(frame)
      }

      override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        webSocket.close(code, reason)
      }

      override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        scope.launch {
          socket = null
          stopPing()
          if (!explicitDisconnect) {
            handleConnectionLoss("closed: $reason")
          }
        }
      }

      override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        scope.launch {
          socket = null
          stopPing()
          addLog(LogLevel.ERROR, "Socket failure", t.message)
          if (!explicitDisconnect) handleConnectionLoss(t.message ?: "socket failure")
        }
      }
    }

  private fun handleFrame(frame: ByteArray) {
    val decoded = codec.decode(frame) ?: return
    if (decoded.type.isBlank()) return
    scope.launch { handleMessage(decoded.type, decoded.payloadJson) }
  }

  private fun handleMessage(type: String, payload: String?) {
    when (type) {
      MessageType.ROOM_CREATED -> {
        val data = decode(payload, RoomCreatedPayload.serializer()) ?: return
        _roomState.value =
          RoomState(
            roomCode = data.roomCode,
            hostId = data.userId,
            users =
              listOf(
                UserInfo(
                  userId = data.userId,
                  username = ownUsername,
                  isHost = true,
                  isConnected = true,
                ),
              ),
          )
        _role.value = RoomRole.HOST
        _userId.value = data.userId
        roomCode = data.roomCode
        sessionToken = data.sessionToken
        sessionTimestamp = System.currentTimeMillis()
        persistSession()
        acquireWakeLock()
        emit(ListenTogetherEvent.RoomCreated(data.roomCode, data.userId))
      }
      MessageType.JOIN_APPROVED -> {
        val data = decode(payload, JoinApprovedPayload.serializer()) ?: return
        _role.value = RoomRole.GUEST
        _userId.value = data.userId
        roomCode = data.roomCode
        sessionToken = data.sessionToken
        sessionTimestamp = System.currentTimeMillis()
        data.state?.let { _roomState.value = it }
        persistSession()
        acquireWakeLock()
        emit(ListenTogetherEvent.JoinApproved(data.roomCode, data.userId, data.state))
      }
      MessageType.JOIN_REJECTED -> {
        val data = decode(payload, JoinRejectedPayload.serializer()) ?: return
        clearSessionState()
        clearPersistedSession()
        emit(ListenTogetherEvent.JoinRejected(data.reason))
      }
      MessageType.JOIN_REQUEST -> {
        val request = decode(payload, JoinRequestPayload.serializer()) ?: return
        if (request.username in _blockedUsernames.value) {
          rejectJoin(request.userId, "You are blocked")
        } else {
          _pendingJoinRequests.update { it + request }
          emit(ListenTogetherEvent.JoinRequestReceived(request.userId, request.username))
          if (context.dataStore[ListenTogetherAutoApprovalKey] == true) {
            approveJoin(request.userId)
          } else {
            postJoinNotification(request)
          }
        }
      }
      MessageType.USER_JOINED -> {
        val data = decode(payload, UserJoinedPayload.serializer()) ?: return
        _pendingJoinRequests.update { list -> list.filterNot { it.userId == data.userId } }
        _roomState.update { state ->
          state?.copy(
            users =
              state.users.filterNot { it.userId == data.userId } +
                UserInfo(userId = data.userId, username = data.username, isConnected = true),
          )
        }
        cancelJoinNotification()
        emit(ListenTogetherEvent.UserJoined(data.userId, data.username))
      }
      MessageType.USER_LEFT -> {
        val data = decode(payload, UserLeftPayload.serializer()) ?: return
        _roomState.update { state ->
          state?.copy(users = state.users.filterNot { it.userId == data.userId })
        }
        emit(ListenTogetherEvent.UserLeft(data.userId, data.username))
      }
      MessageType.HOST_CHANGED -> {
        val data = decode(payload, HostChangedPayload.serializer()) ?: return
        _roomState.update { state ->
          state?.copy(
            hostId = data.newHostId,
            users =
              state.users.map { user ->
                user.copy(isHost = user.userId == data.newHostId)
              },
          )
        }
        _role.value =
          if (_userId.value == data.newHostId) RoomRole.HOST else RoomRole.GUEST
        emit(ListenTogetherEvent.HostChanged(data.newHostId, data.newHostName))
      }
      MessageType.KICKED -> {
        val data = decode(payload, KickedPayload.serializer()) ?: return
        clearSessionState()
        clearPersistedSession()
        releaseWakeLock()
        emit(ListenTogetherEvent.Kicked(data.reason))
      }
      MessageType.SYNC_PLAYBACK -> {
        val data = decode(payload, PlaybackActionPayload.serializer()) ?: return
        emit(ListenTogetherEvent.PlaybackSync(data))
      }
      MessageType.BUFFER_WAIT -> {
        val data = decode(payload, BufferWaitPayload.serializer()) ?: return
        _bufferingUsers.value = data.waitingFor
        emit(ListenTogetherEvent.BufferWait(data.trackId, data.waitingFor))
      }
      MessageType.BUFFER_COMPLETE -> {
        val data = decode(payload, BufferCompletePayload.serializer()) ?: return
        _bufferingUsers.value = emptyList()
        emit(ListenTogetherEvent.BufferComplete(data.trackId))
      }
      MessageType.SYNC_STATE -> {
        val data = decode(payload, SyncStatePayload.serializer()) ?: return
        emit(ListenTogetherEvent.SyncStateReceived(data))
      }
      MessageType.ERROR -> {
        val data = decode(payload, ErrorPayload.serializer()) ?: return
        emit(ListenTogetherEvent.ServerError(data.code, data.message))
        if (sessionToken != null || roomCode != null) {
          val failedRoom = roomCode
          val name = ownUsername
          if (failedRoom != null && name.isNotBlank()) {
            scope.launch {
              delay(500)
              joinRoom(failedRoom, name)
            }
          } else {
            clearSessionState()
            clearPersistedSession()
          }
        }
      }
      MessageType.PONG -> {
        val sent = pingSentAt
        if (sent > 0) {
          _rtt.value = (System.currentTimeMillis() - sent).coerceAtLeast(0)
          pingSentAt = 0
        }
      }
      MessageType.RECONNECTED -> {
        val data = decode(payload, ReconnectedPayload.serializer()) ?: return
        roomCode = data.roomCode
        _userId.value = data.userId
        _roomState.value = data.state
        _role.value =
          if (data.isHost || data.state?.hostId == data.userId) RoomRole.HOST else RoomRole.GUEST
        sessionTimestamp = System.currentTimeMillis()
        reconnectAttempt = 0
        persistSession()
        acquireWakeLock()
        emit(ListenTogetherEvent.Reconnected(data.roomCode, data.userId, data.state, data.isHost))
      }
      MessageType.USER_RECONNECTED -> {
        val data = decode(payload, UserReconnectedPayload.serializer()) ?: return
        _roomState.update { state ->
          state?.copy(
            users =
              state.users.map { user ->
                if (user.userId == data.userId) user.copy(isConnected = true) else user
              },
          )
        }
        emit(ListenTogetherEvent.UserReconnected(data.userId, data.username))
      }
      MessageType.USER_DISCONNECTED -> {
        val data = decode(payload, UserDisconnectedPayload.serializer()) ?: return
        _roomState.update { state ->
          state?.copy(
            users =
              state.users.map { user ->
                if (user.userId == data.userId) user.copy(isConnected = false) else user
              },
          )
        }
        emit(ListenTogetherEvent.UserDisconnected(data.userId, data.username))
      }
      MessageType.SUGGESTION_RECEIVED -> {
        val data = decode(payload, SuggestionReceivedPayload.serializer()) ?: return
        _pendingSuggestions.update { it + data }
      }
      MessageType.SUGGESTION_APPROVED -> {
        val data = decode(payload, SuggestionApprovedPayload.serializer()) ?: return
        _pendingSuggestions.update { list ->
          list.filterNot { it.suggestionId == data.suggestionId }
        }
        emit(ListenTogetherEvent.LocalSuggestionApproved(data))
      }
      MessageType.SUGGESTION_REJECTED -> {
        val data = decode(payload, SuggestionRejectedPayload.serializer()) ?: return
        _pendingSuggestions.update { list ->
          list.filterNot { it.suggestionId == data.suggestionId }
        }
      }
      MessageType.ROOM_SETTINGS_CHANGED -> {
        val data = decode(payload, UpdateRoomSettingsPayload.serializer()) ?: return
        _roomState.update { it?.copy(allowParticipantControl = data.allowParticipantControl) }
        emit(ListenTogetherEvent.RoomSettingsChanged(data.allowParticipantControl))
      }
      MessageType.CHAT -> {
        val data = decode(payload, ChatMessagePayload.serializer()) ?: return
        emit(ListenTogetherEvent.ChatMessageReceived(data))
      }
    }
  }

  // ------------------------------------------------------------------ reconnection

  private fun handleConnectionLoss(reason: String) {
    val hasSession = sessionToken != null || roomCode != null || pendingAction != null
    if (!hasSession) {
      _connectionState.value = ConnectionState.DISCONNECTED
      emit(ListenTogetherEvent.Disconnected)
      return
    }
    if (reconnectAttempt >= MAX_RECONNECT_ATTEMPTS) {
      _connectionState.value = ConnectionState.ERROR
      emit(ListenTogetherEvent.ConnectionError(reason))
      addLog(LogLevel.ERROR, "Reconnect failed", reason)
      return
    }
    reconnectAttempt += 1
    val base = min(1000L * (1L shl (reconnectAttempt - 1)), 120_000L)
    val jitter = (base * 0.2 * Random.nextDouble()).roundToLong()
    _connectionState.value = ConnectionState.RECONNECTING
    emit(ListenTogetherEvent.Reconnecting(reconnectAttempt, MAX_RECONNECT_ATTEMPTS))
    addLog(LogLevel.WARNING, "Reconnecting", "attempt $reconnectAttempt in ${base + jitter} ms")
    reconnectJob?.cancel()
    reconnectJob =
      scope.launch {
        delay(base + jitter)
        if (!explicitDisconnect && socket == null) connect()
      }
  }

  private fun startPing() {
    stopPing()
    pingJob =
      scope.launch {
        while (isActive) {
          delay(PING_INTERVAL_MS)
          if (socket == null) break
          pingSentAt = System.currentTimeMillis()
          send(MessageType.PING)
        }
      }
  }

  private fun stopPing() {
    pingJob?.cancel()
    pingJob = null
  }

  // ------------------------------------------------------------------ send / decode

  private fun sendPending() {
    val action = pendingAction ?: return
    pendingAction = null
    when (action) {
      is PendingAction.Create ->
        send(
          MessageType.CREATE_ROOM,
          json.encodeToString(CreateRoomPayload.serializer(), CreateRoomPayload(action.username)),
        )
      is PendingAction.Join ->
        send(
          MessageType.JOIN_ROOM,
          json.encodeToString(
            JoinRoomPayload.serializer(),
            JoinRoomPayload(action.roomCode, action.username),
          ),
        )
    }
  }

  private fun send(type: String, payload: String? = null): Boolean {
    val ws = socket ?: return false
    val frame = codec.encode(type, payload)
    return if (codec.useProtobuf) ws.send(ByteString.of(*frame)) else ws.send(String(frame))
  }

  private fun <T> decode(payload: String?, serializer: kotlinx.serialization.KSerializer<T>): T? =
    payload?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }

  private fun emit(event: ListenTogetherEvent) {
    _events.tryEmit(event)
  }

  private fun addLog(level: LogLevel, message: String, details: String? = null) {
    val entry =
      LogEntry(
        timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()),
        level = level,
        message = message,
        details = details,
      )
    _logs.update { (it + entry).takeLast(300) }
    Timber.tag("ListenTogether").log(
      when (level) {
        LogLevel.ERROR -> android.util.Log.ERROR
        LogLevel.WARNING -> android.util.Log.WARN
        LogLevel.DEBUG -> android.util.Log.DEBUG
        LogLevel.INFO -> android.util.Log.INFO
      },
      "$message${details?.let { " -- $it" }.orEmpty()}",
    )
  }

  // ------------------------------------------------------------------ persistence

  private suspend fun restoreSession() {
    val preferences = context.dataStore.data.first()
    val token = preferences[ListenTogetherSessionTokenKey]
    val code = preferences[ListenTogetherRoomCodeKey]
    val timestamp = preferences[ListenTogetherSessionTimestampKey] ?: 0L
    if (token != null && code != null && System.currentTimeMillis() - timestamp < SESSION_TTL_MS) {
      sessionToken = token
      roomCode = code
      sessionTimestamp = timestamp
      _userId.value = preferences[ListenTogetherUserIdKey]
      _role.value =
        if (preferences[ListenTogetherIsHostKey] == true) RoomRole.HOST else RoomRole.GUEST
    } else if (token != null || code != null) {
      clearPersistedSession()
    }
  }

  private fun persistSession() {
    val token = sessionToken ?: return
    val code = roomCode ?: return
    val uid = _userId.value
    val host = _role.value == RoomRole.HOST
    val timestamp = sessionTimestamp
    scope.launch {
      context.dataStore.edit { preferences ->
        preferences[ListenTogetherSessionTokenKey] = token
        preferences[ListenTogetherRoomCodeKey] = code
        uid?.let { preferences[ListenTogetherUserIdKey] = it }
        preferences[ListenTogetherIsHostKey] = host
        preferences[ListenTogetherSessionTimestampKey] = timestamp
      }
    }
  }

  private fun clearPersistedSession() {
    sessionToken = null
    roomCode = null
    sessionTimestamp = 0
    scope.launch {
      context.dataStore.edit { preferences ->
        preferences.remove(ListenTogetherSessionTokenKey)
        preferences.remove(ListenTogetherRoomCodeKey)
        preferences.remove(ListenTogetherUserIdKey)
        preferences.remove(ListenTogetherIsHostKey)
        preferences.remove(ListenTogetherSessionTimestampKey)
      }
    }
  }

  private fun clearSessionState() {
    sessionToken = null
    roomCode = null
    sessionTimestamp = 0
    _roomState.value = null
    _role.value = RoomRole.NONE
    _userId.value = null
    _pendingJoinRequests.value = emptyList()
    _bufferingUsers.value = emptyList()
    _pendingSuggestions.value = emptyList()
  }

  private suspend fun restoreBlockedUsers() {
    val raw = context.dataStore.data.first()[ListenTogetherBlockedUsersKey] ?: return
    val values =
      runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
    _blockedUsernames.value = values.toSet()
  }

  private fun persistBlockedUsers() {
    val raw = json.encodeToString(_blockedUsernames.value.toList())
    scope.launch {
      context.dataStore.edit { it[ListenTogetherBlockedUsersKey] = raw }
    }
  }

  // ------------------------------------------------------------------ wake lock / notifications

  private fun acquireWakeLock() {
    if (wakeLock?.isHeld == true) return
    val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
    wakeLock =
      power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Revenge:ListenTogether").also {
        it.setReferenceCounted(false)
        runCatching { it.acquire(10 * 60 * 1000L) }
      }
  }

  private fun releaseWakeLock() {
    runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
    wakeLock = null
  }

  private fun postJoinNotification(request: JoinRequestPayload) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      ?: return
    manager.createNotificationChannel(
      NotificationChannel(
        NOTIFICATION_CHANNEL,
        "Listen Together",
        NotificationManager.IMPORTANCE_HIGH,
      ),
    )
    val approve =
      PendingIntent.getBroadcast(
        context,
        request.userId.hashCode(),
        ListenTogetherActionReceiver.intent(context, ListenTogetherActionReceiver.ACTION_APPROVE, request.userId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val reject =
      PendingIntent.getBroadcast(
        context,
        request.userId.hashCode() + 1,
        ListenTogetherActionReceiver.intent(context, ListenTogetherActionReceiver.ACTION_REJECT, request.userId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val notification =
      NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
        .setSmallIcon(R.drawable.person)
        .setContentTitle("Join request")
        .setContentText("${request.username} wants to join your room")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .addAction(0, "Approve", approve)
        .addAction(0, "Reject", reject)
        .setAutoCancel(true)
        .build()
    runCatching { manager.notify(NOTIFICATION_ID, notification) }
  }

  private fun cancelJoinNotification() {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    runCatching { manager?.cancel(NOTIFICATION_ID) }
  }
}
