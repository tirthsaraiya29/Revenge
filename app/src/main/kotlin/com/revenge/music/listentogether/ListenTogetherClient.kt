package com.revenge.music.listentogether

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.PowerManager
import android.util.Base64
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import com.revenge.music.R
import com.revenge.music.constants.ListenTogetherAutoApprovalKey
import com.revenge.music.constants.ListenTogetherBlockedUsersKey
import com.revenge.music.constants.ListenTogetherIsHostKey
import com.revenge.music.constants.ListenTogetherRoomCodeKey
import com.revenge.music.constants.ListenTogetherServerUrlKey
import com.revenge.music.constants.ListenTogetherSessionTimestampKey
import com.revenge.music.constants.ListenTogetherSessionTokenKey
import com.revenge.music.constants.ListenTogetherUserIdKey
import com.revenge.music.constants.ListenTogetherUsernameKey
import com.revenge.music.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, ERROR }
enum class RoomRole { HOST, GUEST, NONE }
enum class LogLevel { INFO, WARNING, ERROR, DEBUG }

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val message: String,
    val details: String? = null,
)

sealed class PendingAction {
    data class CreateRoom(val username: String) : PendingAction()
    data class JoinRoom(val roomCode: String, val username: String) : PendingAction()
}

sealed class ListenTogetherEvent {
    data class Connected(val userId: String) : ListenTogetherEvent()
    object Disconnected : ListenTogetherEvent()
    data class ConnectionError(val error: String) : ListenTogetherEvent()
    data class Reconnecting(val attempt: Int, val maxAttempts: Int) : ListenTogetherEvent()
    data class RoomCreated(val roomCode: String, val userId: String) : ListenTogetherEvent()
    data class JoinRequestReceived(val userId: String, val username: String) : ListenTogetherEvent()
    data class JoinApproved(
        val roomCode: String,
        val userId: String,
        val state: RoomState?,
    ) : ListenTogetherEvent()
    data class JoinRejected(val reason: String?) : ListenTogetherEvent()
    data class UserJoined(val userId: String, val username: String) : ListenTogetherEvent()
    data class UserLeft(val userId: String, val username: String) : ListenTogetherEvent()
    data class HostChanged(val newHostId: String, val newHostName: String) : ListenTogetherEvent()
    data class Kicked(val reason: String?) : ListenTogetherEvent()
    data class Reconnected(
        val roomCode: String,
        val userId: String,
        val state: RoomState?,
        val isHost: Boolean,
    ) : ListenTogetherEvent()
    data class UserReconnected(val userId: String, val username: String) : ListenTogetherEvent()
    data class UserDisconnected(val userId: String, val username: String) : ListenTogetherEvent()
    data class PlaybackSync(val action: PlaybackActionPayload) : ListenTogetherEvent()
    data class BufferWait(val trackId: String, val waitingFor: List<String>) : ListenTogetherEvent()
    data class BufferComplete(val trackId: String) : ListenTogetherEvent()
    data class SyncStateReceived(val state: SyncStatePayload) : ListenTogetherEvent()
    data class ServerError(val code: String, val message: String) : ListenTogetherEvent()
    data class ChatMessageReceived(val payload: ChatMessagePayload) : ListenTogetherEvent()
    data class LocalSuggestionApproved(
        val payload: SuggestionReceivedPayload,
    ) : ListenTogetherEvent()
    data class RoomSettingsChanged(val allowParticipantControl: Boolean) : ListenTogetherEvent()
}

@Singleton
class ListenTogetherClient @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val ACTION_APPROVE_JOIN = "com.revenge.music.listentogether.APPROVE_JOIN"
        const val ACTION_REJECT_JOIN = "com.revenge.music.listentogether.REJECT_JOIN"
        const val ACTION_APPROVE_SUGGESTION =
            "com.revenge.music.listentogether.APPROVE_SUGGESTION"
        const val ACTION_REJECT_SUGGESTION =
            "com.revenge.music.listentogether.REJECT_SUGGESTION"
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_SUGGESTION_ID = "extra_suggestion_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        private const val CHANNEL_ID = "listen_together_channel"
        private const val WAKE_LOCK_TAG = "revenge:ListenTogether"
        private const val MAX_RECONNECT_ATTEMPTS = 15
        private const val PING_INTERVAL_MS = 25_000L
        private const val SESSION_GRACE_MS = 10 * 60 * 1000L
        private const val MAX_LOG_ENTRIES = 500
        private const val EVENT_BUFFER = 64
        private const val REPLY_PREFIX = "​[RPLY:"
        private const val REPLY_SUFFIX = "]​"

        @Volatile private var instance: ListenTogetherClient? = null

        fun getInstance(): ListenTogetherClient? = instance

        fun setInstance(client: ListenTogetherClient?) {
            instance = client
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val codec = MessageCodec()
    private val json = MessageCodec.DEFAULT_JSON

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _roomState = MutableStateFlow<RoomState?>(null)
    val roomState: StateFlow<RoomState?> = _roomState.asStateFlow()

    private val _role = MutableStateFlow(RoomRole.NONE)
    val role: StateFlow<RoomRole> = _role.asStateFlow()

    private val _userId = MutableStateFlow<String?>(null)
    val userId: StateFlow<String?> = _userId.asStateFlow()

    private val _pendingJoinRequests = MutableStateFlow<List<JoinRequestPayload>>(emptyList())
    val pendingJoinRequests: StateFlow<List<JoinRequestPayload>> =
        _pendingJoinRequests.asStateFlow()

    private val _bufferingUsers = MutableStateFlow<List<String>>(emptyList())
    val bufferingUsers: StateFlow<List<String>> = _bufferingUsers.asStateFlow()

    private val _pendingSuggestions = MutableStateFlow<List<SuggestionReceivedPayload>>(emptyList())
    val pendingSuggestions: StateFlow<List<SuggestionReceivedPayload>> =
        _pendingSuggestions.asStateFlow()

    private val _blockedUsernames = MutableStateFlow<Set<String>>(emptySet())
    val blockedUsernames: StateFlow<Set<String>> = _blockedUsernames.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _events = MutableSharedFlow<ListenTogetherEvent>(
        extraBufferCapacity = EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<ListenTogetherEvent> = _events.asSharedFlow()

    private val _rtt = MutableStateFlow(0L)
    val rtt: StateFlow<Long> = _rtt.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var sessionToken: String? = null
    @Volatile private var storedRoomCode: String? = null
    @Volatile private var sessionTimestamp: Long = 0L
    @Volatile private var currentUsername: String = ""
    @Volatile private var pendingAction: PendingAction? = null
    @Volatile private var pingSentAt: Long = 0L
    @Volatile private var networkAvailable: Boolean = true
    @Volatile private var reconnectAttempts: Int = 0
    @Volatile private var reconnectJob: Job? = null
    @Volatile private var pingJob: Job? = null
    @Volatile private var wakeLock: PowerManager.WakeLock? = null

    private val notificationIdsByUser = mutableMapOf<String, Int>()
    private val notificationIdsBySuggestion = mutableMapOf<String, Int>()

    private val connectivityManager =
        context.getSystemService(ConnectivityManager::class.java)

    init {
        setInstance(this)
        createNotificationChannel()
        registerNetworkObserver()
        scope.launch { restoreState() }
    }

    val isInRoom: Boolean get() = _roomState.value != null
    val isHost: Boolean get() = _role.value == RoomRole.HOST
    val hasPersistedSession: Boolean get() = sessionToken != null && storedRoomCode != null

    // ------------------------------------------------------------------ lifecycle

    fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.CONNECTING
        ) {
            log(LogLevel.WARNING, "Already connected or connecting")
            return
        }
        _connectionState.value = ConnectionState.CONNECTING
        scope.launch {
            val url = runCatching {
                context.dataStore.data.first()[ListenTogetherServerUrlKey]
            }.getOrNull().orEmpty().ifBlank { ListenTogetherServers.defaultServerUrl }
            codec.reset()
            openSocket(url)
        }
    }

    fun disconnect() {
        reconnectJob?.cancel()
        cancelPing()
        releaseWakeLock()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        clearRoomState()
        _connectionState.value = ConnectionState.DISCONNECTED
        emit(ListenTogetherEvent.Disconnected)
    }

    fun forceReconnect() {
        reconnectAttempts = 0
        reconnectJob?.cancel()
        cancelPing()
        webSocket?.close(1000, "Force reconnect")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
        scope.launch {
            delay(500)
            connect()
        }
    }

    private suspend fun openSocket(url: String) {
        val request = runCatching { Request.Builder().url(url).build() }.getOrElse {
            failConnection("Invalid server URL")
            return
        }
        webSocket = httpClient.newWebSocket(request, socketListener)
    }

    private val socketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _connectionState.value = ConnectionState.CONNECTED
            reconnectAttempts = 0
            log(LogLevel.INFO, "Connected to server")
            emit(ListenTogetherEvent.Connected(_userId.value.orEmpty()))
            startPingLoop()
            val token = sessionToken
            val room = storedRoomCode
            if (token != null && room != null) {
                send(MessageTypes.RECONNECT, ReconnectPayload(token))
            } else {
                pendingAction?.let { action ->
                    pendingAction = null
                    executePending(action)
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleFrame(text.toByteArray(Charsets.UTF_8))
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            handleFrame(bytes.toByteArray())
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            log(LogLevel.ERROR, "Socket failure: ${t.message}")
            onSocketLost()
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            log(LogLevel.INFO, "Socket closing: $code $reason")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            log(LogLevel.INFO, "Socket closed: $code $reason")
            onSocketLost()
        }
    }

    private fun onSocketLost() {
        cancelPing()
        webSocket = null
        if (!networkAvailable) {
            _connectionState.value = ConnectionState.DISCONNECTED
            emit(ListenTogetherEvent.Disconnected)
            return
        }
        if (!hasReconnectContext()) {
            _connectionState.value = ConnectionState.DISCONNECTED
            emit(ListenTogetherEvent.Disconnected)
            return
        }
        scheduleReconnect()
    }

    private fun hasReconnectContext(): Boolean =
        sessionToken != null || _roomState.value != null || pendingAction != null

    private fun scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            _connectionState.value = ConnectionState.ERROR
            emit(ListenTogetherEvent.ConnectionError("Reconnect attempts exhausted"))
            if (sessionToken == null) scope.launch { clearPersistedSession() }
            return
        }
        reconnectAttempts++
        val attempt = reconnectAttempts
        _connectionState.value = ConnectionState.RECONNECTING
        emit(ListenTogetherEvent.Reconnecting(attempt, MAX_RECONNECT_ATTEMPTS))
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(backoffDelay(attempt))
            if (!networkAvailable || !hasReconnectContext()) return@launch
            _connectionState.value = ConnectionState.CONNECTING
            val url = runCatching {
                context.dataStore.data.first()[ListenTogetherServerUrlKey]
            }.getOrNull().orEmpty().ifBlank { ListenTogetherServers.defaultServerUrl }
            openSocket(url)
        }
    }

    private fun backoffDelay(attempt: Int): Long {
        val exponent = min(attempt - 1, 4) + 1
        val base = min(1_000L * (1L shl exponent), 120_000L)
        val jitter = (base * Random.nextDouble(0.0, 0.2)).toLong()
        return base + jitter
    }

    private fun executePending(action: PendingAction) {
        when (action) {
            is PendingAction.CreateRoom ->
                send(MessageTypes.CREATE_ROOM, CreateRoomPayload(action.username))
            is PendingAction.JoinRoom -> send(
                MessageTypes.JOIN_ROOM,
                JoinRoomPayload(action.roomCode.uppercase(), action.username),
            )
        }
    }

    private fun startPingLoop() {
        cancelPing()
        pingJob = scope.launch {
            while (true) {
                delay(PING_INTERVAL_MS)
                pingSentAt = System.currentTimeMillis()
                send(MessageTypes.PING, null)
            }
        }
    }

    private fun cancelPing() {
        pingJob?.cancel()
        pingJob = null
        pingSentAt = 0L
    }

    private fun failConnection(message: String) {
        _connectionState.value = ConnectionState.ERROR
        log(LogLevel.ERROR, message)
        emit(ListenTogetherEvent.ConnectionError(message))
    }

    // ------------------------------------------------------------------ room actions

    fun createRoom(username: String) {
        scope.launch { clearPersistedSession() }
        currentUsername = username
        scope.launch { storeUsername(username) }
        pendingAction = null
        when (_connectionState.value) {
            ConnectionState.CONNECTED ->
                send(MessageTypes.CREATE_ROOM, CreateRoomPayload(username))
            else -> {
                pendingAction = PendingAction.CreateRoom(username)
                connect()
            }
        }
    }

    fun joinRoom(roomCode: String, username: String) {
        scope.launch { clearPersistedSession() }
        currentUsername = username
        scope.launch { storeUsername(username) }
        pendingAction = null
        val normalized = roomCode.uppercase()
        when (_connectionState.value) {
            ConnectionState.CONNECTED ->
                send(MessageTypes.JOIN_ROOM, JoinRoomPayload(normalized, username))
            else -> {
                pendingAction = PendingAction.JoinRoom(normalized, username)
                connect()
            }
        }
    }

    fun leaveRoom() {
        send(MessageTypes.LEAVE_ROOM, null)
        releaseWakeLock()
        clearRoomState()
        scope.launch { clearPersistedSession() }
    }

    fun approveJoin(userId: String) {
        if (!requireHost("approveJoin")) return
        send(MessageTypes.APPROVE_JOIN, ApproveJoinPayload(userId))
    }

    fun rejectJoin(userId: String, reason: String? = null) {
        if (!requireHost("rejectJoin")) return
        send(MessageTypes.REJECT_JOIN, RejectJoinPayload(userId, reason))
    }

    fun kickUser(userId: String, reason: String? = null) {
        if (!requireHost("kickUser")) return
        send(MessageTypes.KICK_USER, KickUserPayload(userId, reason))
    }

    fun transferHost(newHostId: String) {
        if (!requireHost("transferHost")) return
        send(MessageTypes.TRANSFER_HOST, TransferHostPayload(newHostId))
    }

    fun updateRoomSettings(allowParticipantControl: Boolean) {
        if (!requireHost("updateRoomSettings")) return
        _roomState.update { it?.copy(allowParticipantControl = allowParticipantControl) }
        send(
            MessageTypes.UPDATE_ROOM_SETTINGS,
            UpdateRoomSettingsPayload(allowParticipantControl),
        )
    }

    fun sendPlaybackAction(
        action: String,
        trackId: String? = null,
        position: Long? = null,
        trackInfo: TrackInfo? = null,
        insertNext: Boolean? = null,
        queue: List<TrackInfo>? = null,
        queueTitle: String? = null,
        volume: Float? = null,
    ) {
        if (!canControlPlayback()) {
            log(LogLevel.ERROR, "Playback control not permitted")
            return
        }
        send(
            MessageTypes.PLAYBACK_ACTION,
            PlaybackActionPayload(
                action = action,
                trackId = trackId,
                position = position,
                trackInfo = trackInfo,
                insertNext = insertNext ?: false,
                queue = queue.orEmpty(),
                queueTitle = queueTitle,
                volume = volume,
            ),
        )
    }

    fun sendChatMessage(message: String, replyTo: RepliedMessage? = null) {
        if (!isInRoom) {
            log(LogLevel.WARNING, "Cannot chat outside a room")
            return
        }
        if (message.isBlank()) return
        send(
            MessageTypes.CHAT,
            ChatPayload(message = encodeChatMessage(message, replyTo), replyTo = replyTo),
        )
    }

    fun sendBufferReady(trackId: String) {
        if (!isInRoom) return
        send(MessageTypes.BUFFER_READY, BufferReadyPayload(trackId))
    }

    fun suggestTrack(trackInfo: TrackInfo) {
        if (!isInRoom) return
        if (_role.value == RoomRole.HOST) {
            log(LogLevel.WARNING, "Hosts cannot suggest tracks")
            return
        }
        send(MessageTypes.SUGGEST_TRACK, SuggestTrackPayload(trackInfo))
    }

    fun approveSuggestion(suggestionId: String) {
        if (!requireHost("approveSuggestion")) return
        _pendingSuggestions.value.firstOrNull { it.suggestionId == suggestionId }?.let {
            emit(ListenTogetherEvent.LocalSuggestionApproved(it))
        }
        _pendingSuggestions.update { list -> list.filterNot { it.suggestionId == suggestionId } }
        cancelSuggestionNotification(suggestionId)
        send(MessageTypes.APPROVE_SUGGESTION, ApproveSuggestionPayload(suggestionId))
    }

    fun rejectSuggestion(suggestionId: String, reason: String? = null) {
        if (!requireHost("rejectSuggestion")) return
        _pendingSuggestions.update { list -> list.filterNot { it.suggestionId == suggestionId } }
        cancelSuggestionNotification(suggestionId)
        send(MessageTypes.REJECT_SUGGESTION, RejectSuggestionPayload(suggestionId, reason))
    }

    fun requestSync() {
        if (_roomState.value == null) return
        send(MessageTypes.REQUEST_SYNC, null)
    }

    fun blockUser(username: String) {
        val trimmed = username.trim()
        if (trimmed.isEmpty()) return
        _blockedUsernames.update { it + trimmed }
        _pendingJoinRequests.update { list -> list.filterNot { it.username == trimmed } }
        _pendingSuggestions.update { list -> list.filterNot { it.fromUsername == trimmed } }
        scope.launch { persistBlockedUsers() }
    }

    fun unblockUser(username: String) {
        _blockedUsernames.update { it - username.trim() }
        scope.launch { persistBlockedUsers() }
    }

    fun isUserBlocked(username: String): Boolean =
        username.trim() in _blockedUsernames.value

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun getPersistedRoomCode(): String? = storedRoomCode

    fun getSessionAge(): Long =
        if (sessionTimestamp <= 0L) 0L else System.currentTimeMillis() - sessionTimestamp

    // ------------------------------------------------------------------ helpers

    private fun requireHost(action: String): Boolean {
        if (_role.value == RoomRole.HOST) return true
        log(LogLevel.ERROR, "$action requires host role")
        return false
    }

    private fun canControlPlayback(): Boolean {
        if (!isInRoom) return false
        return _role.value == RoomRole.HOST ||
            _roomState.value?.allowParticipantControl == true
    }

    private fun clearRoomState() {
        _roomState.value = null
        _role.value = RoomRole.NONE
        _userId.value = null
        _pendingJoinRequests.value = emptyList()
        _pendingSuggestions.value = emptyList()
        _bufferingUsers.value = emptyList()
        sessionToken = null
        storedRoomCode = null
        sessionTimestamp = 0L
    }

    private fun send(type: String, payload: Any?) {
        val socket = webSocket
        if (socket == null) {
            log(LogLevel.WARNING, "Cannot send $type: socket closed")
            return
        }
        val bytes = codec.encode(type, payload)
        if (bytes == null) {
            log(LogLevel.ERROR, "Failed to encode $type")
            return
        }
        val sent = runCatching { socket.send(bytes.toByteString()) }.getOrDefault(false)
        if (!sent) log(LogLevel.ERROR, "Failed to send $type")
    }

    private fun log(level: LogLevel, message: String, details: String? = null) {
        _logs.update { current ->
            val next = current + LogEntry(System.currentTimeMillis(), level, message, details)
            if (next.size > MAX_LOG_ENTRIES) next.takeLast(MAX_LOG_ENTRIES) else next
        }
    }

    private fun emit(event: ListenTogetherEvent) {
        _events.tryEmit(event)
    }

    // ------------------------------------------------------------------ inbound

    private fun handleFrame(frame: ByteArray) {
        val decoded = codec.decode(frame)
        if (decoded == null) {
            log(LogLevel.ERROR, "Failed to decode frame")
            return
        }
        if (decoded.format == MessageFormat.PROTOBUF &&
            codec.outgoingFormat != MessageFormat.PROTOBUF
        ) {
            codec.upgradeToProtobuf()
        }
        val payload = codec.decodePayload(decoded.type, decoded.payload, decoded.format)
        runCatching { handleMessage(decoded.type, payload) }.onFailure {
            log(LogLevel.ERROR, "Failed to handle ${decoded.type}", it.message)
        }
    }

    private fun handleMessage(type: String, payload: Any?) {
        when (type) {
            MessageTypes.ROOM_CREATED -> onRoomCreated(payload as? RoomCreatedPayload ?: return)
            MessageTypes.JOIN_REQUEST -> onJoinRequest(payload as? JoinRequestPayload ?: return)
            MessageTypes.JOIN_APPROVED -> onJoinApproved(payload as? JoinApprovedPayload ?: return)
            MessageTypes.JOIN_REJECTED -> onJoinRejected(payload as? JoinRejectedPayload ?: return)
            MessageTypes.USER_JOINED -> onUserJoined(payload as? UserJoinedPayload ?: return)
            MessageTypes.USER_LEFT -> onUserLeft(payload as? UserLeftPayload ?: return)
            MessageTypes.HOST_CHANGED -> onHostChanged(payload as? HostChangedPayload ?: return)
            MessageTypes.KICKED -> onKicked(payload as? KickedPayload ?: return)
            MessageTypes.SYNC_PLAYBACK ->
                onPlayback(payload as? PlaybackActionPayload ?: return)
            MessageTypes.BUFFER_WAIT -> onBufferWait(payload as? BufferWaitPayload ?: return)
            MessageTypes.BUFFER_COMPLETE ->
                onBufferComplete(payload as? BufferCompletePayload ?: return)
            MessageTypes.SYNC_STATE ->
                emit(ListenTogetherEvent.SyncStateReceived(payload as? SyncStatePayload ?: return))
            MessageTypes.SUGGESTION_RECEIVED ->
                onSuggestionReceived(payload as? SuggestionReceivedPayload ?: return)
            MessageTypes.SUGGESTION_APPROVED ->
                onSuggestionSettled((payload as? SuggestionApprovedPayload)?.suggestionId)
            MessageTypes.SUGGESTION_REJECTED ->
                onSuggestionSettled((payload as? SuggestionRejectedPayload)?.suggestionId)
            MessageTypes.ERROR -> onError(payload as? ErrorPayload ?: return)
            MessageTypes.PONG -> onPong()
            MessageTypes.RECONNECTED ->
                onReconnected(payload as? ReconnectedPayload ?: return)
            MessageTypes.USER_RECONNECTED ->
                onPresence((payload as? UserReconnectedPayload)?.userId, true)
            MessageTypes.USER_DISCONNECTED ->
                onPresence((payload as? UserDisconnectedPayload)?.userId, false)
            MessageTypes.CHAT -> onChat(payload)
            MessageTypes.ROOM_SETTINGS_CHANGED ->
                onRoomSettingsChanged(payload as? UpdateRoomSettingsPayload ?: return)
            else -> log(LogLevel.WARNING, "Unknown message type: $type")
        }
    }

    private fun onRoomCreated(payload: RoomCreatedPayload) {
        _userId.value = payload.userId
        _role.value = RoomRole.HOST
        sessionToken = payload.sessionToken
        storedRoomCode = payload.roomCode
        sessionTimestamp = System.currentTimeMillis()
        _roomState.value = RoomState(
            roomCode = payload.roomCode,
            hostId = payload.userId,
            users = listOf(
                UserInfo(payload.userId, currentUsername, isHost = true, isConnected = true),
            ),
            currentTrack = null,
            isPlaying = false,
            position = 0L,
            lastUpdate = System.currentTimeMillis(),
            volume = 1f,
        )
        scope.launch {
            persistSession(
                token = payload.sessionToken,
                roomCode = payload.roomCode,
                userId = payload.userId,
                isHost = true,
            )
        }
        acquireWakeLock()
        showToast("Room created: ${payload.roomCode}")
        emit(ListenTogetherEvent.RoomCreated(payload.roomCode, payload.userId))
    }

    private fun onJoinRequest(payload: JoinRequestPayload) {
        if (isUserBlocked(payload.username)) {
            log(LogLevel.INFO, "Rejecting blocked user ${payload.username}")
            rejectJoin(payload.userId, "You are blocked")
            return
        }
        _pendingJoinRequests.update { it + payload }
        emit(ListenTogetherEvent.JoinRequestReceived(payload.userId, payload.username))
        if (_role.value != RoomRole.HOST) return

        scope.launch {
            val autoApprove = runCatching {
                context.dataStore.data.first()[ListenTogetherAutoApprovalKey]
            }.getOrNull() ?: false
            if (autoApprove) {
                approveJoin(payload.userId)
            } else {
                notifyJoinRequest(payload)
            }
        }
    }

    private fun onJoinApproved(payload: JoinApprovedPayload) {
        _userId.value = payload.userId
        _role.value = RoomRole.GUEST
        sessionToken = payload.sessionToken
        storedRoomCode = payload.roomCode
        sessionTimestamp = System.currentTimeMillis()
        _roomState.value = payload.state ?: RoomState(
            roomCode = payload.roomCode,
            hostId = "",
            users = listOf(
                UserInfo(payload.userId, currentUsername, isHost = false, isConnected = true),
            ),
            volume = 1f,
        )
        scope.launch {
            persistSession(
                token = payload.sessionToken.orEmpty(),
                roomCode = payload.roomCode,
                userId = payload.userId,
                isHost = false,
            )
        }
        acquireWakeLock()
        emit(ListenTogetherEvent.JoinApproved(payload.roomCode, payload.userId, payload.state))
    }

    private fun onJoinRejected(payload: JoinRejectedPayload) {
        log(LogLevel.WARNING, "Join rejected: ${payload.reason}")
        emit(ListenTogetherEvent.JoinRejected(payload.reason))
    }

    private fun onUserJoined(payload: UserJoinedPayload) {
        _roomState.update { state ->
            state?.let {
                if (it.users.any { user -> user.userId == payload.userId }) it
                else it.copy(
                    users = it.users + UserInfo(
                        payload.userId, payload.username, isHost = false, isConnected = true,
                    ),
                )
            }
        }
        _pendingJoinRequests.update { list -> list.filterNot { it.userId == payload.userId } }
        cancelJoinNotification(payload.userId)
        emit(ListenTogetherEvent.UserJoined(payload.userId, payload.username))
    }

    private fun onUserLeft(payload: UserLeftPayload) {
        _roomState.update { state ->
            state?.copy(users = state.users.filterNot { it.userId == payload.userId })
        }
        emit(ListenTogetherEvent.UserLeft(payload.userId, payload.username))
    }

    private fun onHostChanged(payload: HostChangedPayload) {
        val me = _userId.value
        _roomState.update { state ->
            state?.copy(
                hostId = payload.newHostId,
                users = state.users.map { it.copy(isHost = it.userId == payload.newHostId) },
            )
        }
        _role.value = when {
            payload.newHostId == me -> RoomRole.HOST
            _role.value == RoomRole.HOST -> RoomRole.GUEST
            else -> _role.value
        }
        emit(ListenTogetherEvent.HostChanged(payload.newHostId, payload.newHostName))
    }

    private fun onKicked(payload: KickedPayload) {
        releaseWakeLock()
        clearRoomState()
        scope.launch { clearPersistedSession() }
        emit(ListenTogetherEvent.Kicked(payload.reason))
    }

    private fun onPlayback(payload: PlaybackActionPayload) {
        _roomState.update { applyPlaybackAction(it, payload) }
        emit(ListenTogetherEvent.PlaybackSync(payload))
    }

    private fun applyPlaybackAction(
        current: RoomState?,
        payload: PlaybackActionPayload,
    ): RoomState? {
        if (current == null) return null
        return when (payload.action) {
            PlaybackActions.PLAY -> current.copy(
                isPlaying = true,
                position = payload.position ?: current.position,
                lastUpdate = System.currentTimeMillis(),
            )
            PlaybackActions.PAUSE -> current.copy(
                isPlaying = false,
                position = payload.position ?: current.position,
            )
            PlaybackActions.SEEK -> current.copy(position = payload.position ?: current.position)
            PlaybackActions.CHANGE_TRACK -> current.copy(
                currentTrack = payload.trackInfo ?: current.currentTrack,
                isPlaying = false,
                position = 0L,
            )
            PlaybackActions.QUEUE_ADD -> {
                val track = payload.trackInfo ?: return current
                val queue = if (payload.insertNext) listOf(track) + current.queue
                else current.queue + track
                current.copy(queue = queue)
            }
            PlaybackActions.QUEUE_REMOVE -> current.copy(
                queue = current.queue.filterNot { it.id == payload.trackId },
            )
            PlaybackActions.QUEUE_CLEAR -> current.copy(queue = emptyList())
            PlaybackActions.SET_VOLUME -> current.copy(
                volume = (payload.volume ?: current.volume).coerceIn(0f, 1f),
            )
            else -> current
        }
    }

    private fun onBufferWait(payload: BufferWaitPayload) {
        _bufferingUsers.value = payload.waitingFor
        emit(ListenTogetherEvent.BufferWait(payload.trackId, payload.waitingFor))
    }

    private fun onBufferComplete(payload: BufferCompletePayload) {
        _bufferingUsers.value = emptyList()
        emit(ListenTogetherEvent.BufferComplete(payload.trackId))
    }

    private fun onSuggestionReceived(payload: SuggestionReceivedPayload) {
        if (_role.value != RoomRole.HOST) return
        if (isUserBlocked(payload.fromUsername)) return
        _pendingSuggestions.update { it + payload }
        showToast("${payload.fromUsername} suggested ${payload.trackInfo.title}")
        notifySuggestion(payload)
    }

    private fun onSuggestionSettled(suggestionId: String?) {
        if (suggestionId == null) return
        log(LogLevel.INFO, "Suggestion settled: $suggestionId")
        cancelSuggestionNotification(suggestionId)
    }

    private fun onError(payload: ErrorPayload) {
        log(LogLevel.ERROR, "Server error ${payload.code}: ${payload.message}")
        emit(ListenTogetherEvent.ServerError(payload.code, payload.message))
        if (payload.code == "session_not_found") {
            handleMissingSession()
        }
    }

    private fun handleMissingSession() {
        val room = storedRoomCode
        val username = currentUsername
        if (room != null && username.isNotBlank() && _role.value == RoomRole.GUEST) {
            scope.launch {
                delay(500)
                joinRoom(room, username)
            }
        } else {
            sessionToken = null
            storedRoomCode = null
            sessionTimestamp = 0L
            scope.launch { clearPersistedSession() }
        }
    }

    private fun onPong() {
        if (pingSentAt > 0L) {
            _rtt.value = System.currentTimeMillis() - pingSentAt
            pingSentAt = 0L
        }
    }

    private fun onReconnected(payload: ReconnectedPayload) {
        _userId.value = payload.userId
        _role.value = if (payload.isHost) RoomRole.HOST else RoomRole.GUEST
        storedRoomCode = payload.roomCode
        sessionTimestamp = System.currentTimeMillis()
        _roomState.value = payload.state
        reconnectAttempts = 0
        scope.launch {
            persistSession(
                token = sessionToken.orEmpty(),
                roomCode = payload.roomCode,
                userId = payload.userId,
                isHost = payload.isHost,
            )
        }
        acquireWakeLock()
        emit(
            ListenTogetherEvent.Reconnected(
                payload.roomCode, payload.userId, payload.state, payload.isHost,
            ),
        )
    }

    private fun onPresence(userId: String?, connected: Boolean) {
        if (userId == null) return
        val user = _roomState.value?.users?.firstOrNull { it.userId == userId }
        _roomState.update { state ->
            state?.copy(users = state.users.map { it.copy(isConnected = connected) })
        }
        if (user == null) return
        if (connected) emit(ListenTogetherEvent.UserReconnected(userId, user.username))
        else emit(ListenTogetherEvent.UserDisconnected(userId, user.username))
    }

    private fun onChat(payload: Any?) {
        val message = when (payload) {
            is ChatMessagePayload -> {
                if (payload.replyTo != null) payload
                else {
                    val decoded = decodeChatMessage(payload.message)
                    payload.copy(message = decoded.message, replyTo = decoded.replyTo)
                }
            }
            is ChatPayload -> {
                val decoded = decodeChatMessage(payload.message)
                ChatMessagePayload(
                    userId = "",
                    username = payload.replyTo?.username.orEmpty(),
                    message = decoded.message,
                    timestamp = System.currentTimeMillis(),
                    replyTo = payload.replyTo ?: decoded.replyTo,
                )
            }
            else -> return
        }
        emit(ListenTogetherEvent.ChatMessageReceived(message))
    }

    private fun onRoomSettingsChanged(payload: UpdateRoomSettingsPayload) {
        _roomState.update { it?.copy(allowParticipantControl = payload.allowParticipantControl) }
        emit(ListenTogetherEvent.RoomSettingsChanged(payload.allowParticipantControl))
    }

    // ------------------------------------------------------------------ chat marker

    fun encodeChatMessage(message: String, replyTo: RepliedMessage?): String {
        if (replyTo == null) return message
        val raw = "${replyTo.username}|${replyTo.message}".toByteArray(Charsets.UTF_8)
        val encoded = Base64.encodeToString(raw, Base64.NO_WRAP)
        return REPLY_PREFIX + encoded + REPLY_SUFFIX + message
    }

    fun decodeChatMessage(text: String): ChatMessage {
        if (!text.startsWith(REPLY_PREFIX)) return ChatMessage(text, null)
        val end = text.indexOf(REPLY_SUFFIX, REPLY_PREFIX.length)
        if (end < 0) return ChatMessage(text, null)
        val encoded = text.substring(REPLY_PREFIX.length, end)
        return runCatching {
            val decoded = String(Base64.decode(encoded, Base64.NO_WRAP), Charsets.UTF_8)
            val separator = decoded.indexOf('|')
            if (separator < 0) {
                ChatMessage(text, null)
            } else {
                ChatMessage(
                    message = text.substring(end + REPLY_SUFFIX.length),
                    replyTo = RepliedMessage(
                        decoded.substring(0, separator),
                        decoded.substring(separator + 1),
                    ),
                )
            }
        }.getOrElse {
            log(LogLevel.WARNING, "Malformed chat reply payload")
            ChatMessage(text, null)
        }
    }

    data class ChatMessage(val message: String, val replyTo: RepliedMessage?)

    // ------------------------------------------------------------------ persistence

    private suspend fun restoreState() {
        runCatching {
            val prefs = context.dataStore.data.first()
            currentUsername = prefs[ListenTogetherUsernameKey].orEmpty()
            val token = prefs[ListenTogetherSessionTokenKey].orEmpty()
            val room = prefs[ListenTogetherRoomCodeKey].orEmpty()
            val storedUserId = prefs[ListenTogetherUserIdKey].orEmpty()
            val isHost = prefs[ListenTogetherIsHostKey] ?: false
            val timestamp = prefs[ListenTogetherSessionTimestampKey] ?: 0L
            val blocked = prefs[ListenTogetherBlockedUsersKey].orEmpty()

            if (blocked.isNotBlank()) {
                _blockedUsernames.value = runCatching {
                    json.decodeFromString(ListSerializer(String.serializer()), blocked).toSet()
                }.getOrDefault(emptySet())
            }

            val age = System.currentTimeMillis() - timestamp
            if (token.isNotBlank() && room.isNotBlank() && age in 0 until SESSION_GRACE_MS) {
                sessionToken = token
                storedRoomCode = room
                sessionTimestamp = timestamp
                if (storedUserId.isNotBlank()) _userId.value = storedUserId
                _role.value = if (isHost) RoomRole.HOST else RoomRole.GUEST
            } else if (token.isNotBlank() || room.isNotBlank()) {
                log(LogLevel.WARNING, "Discarding expired Listen Together session")
                clearPersistedSession()
            }
        }.onFailure { log(LogLevel.WARNING, "Failed to restore session", it.message) }
    }

    private suspend fun persistSession(
        token: String,
        roomCode: String,
        userId: String,
        isHost: Boolean,
    ) {
        runCatching {
            context.dataStore.edit { prefs ->
                prefs[ListenTogetherSessionTokenKey] = token
                prefs[ListenTogetherRoomCodeKey] = roomCode
                prefs[ListenTogetherUserIdKey] = userId
                prefs[ListenTogetherIsHostKey] = isHost
                prefs[ListenTogetherSessionTimestampKey] = System.currentTimeMillis()
            }
        }.onFailure { log(LogLevel.WARNING, "Failed to persist session", it.message) }
    }

    private suspend fun clearPersistedSession() {
        runCatching {
            context.dataStore.edit { prefs ->
                prefs.remove(ListenTogetherSessionTokenKey)
                prefs.remove(ListenTogetherRoomCodeKey)
                prefs.remove(ListenTogetherUserIdKey)
                prefs.remove(ListenTogetherIsHostKey)
                prefs.remove(ListenTogetherSessionTimestampKey)
            }
        }.onFailure { log(LogLevel.WARNING, "Failed to clear session", it.message) }
    }

    private suspend fun storeUsername(username: String) {
        runCatching {
            context.dataStore.edit { it[ListenTogetherUsernameKey] = username }
        }
    }

    private suspend fun persistBlockedUsers() {
        runCatching {
            val encoded = json.encodeToString(
                ListSerializer(String.serializer()),
                _blockedUsernames.value.toList(),
            )
            context.dataStore.edit { it[ListenTogetherBlockedUsersKey] = encoded }
        }.onFailure { log(LogLevel.WARNING, "Failed to persist blocked users", it.message) }
    }

    // ------------------------------------------------------------------ notifications

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Listen Together",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Room invitations and suggestions" }
        manager.createNotificationChannel(channel)
    }

    private fun notificationsAllowed(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun notifyJoinRequest(payload: JoinRequestPayload) {
        if (!notificationsAllowed()) {
            log(LogLevel.WARNING, "Notification permission missing")
            return
        }
        runCatching {
            val notificationId = notificationIdsByUser.getOrPut(payload.userId) {
                (System.currentTimeMillis() xor payload.userId.hashCode()).toInt()
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_headphones_generic)
                .setContentTitle("Join request")
                .setContentText("${payload.username} wants to join your room")
                .setAutoCancel(true)
                .addAction(
                    0,
                    "Approve",
                    actionIntent(ACTION_APPROVE_JOIN, payload.userId, null, notificationId),
                )
                .addAction(
                    0,
                    "Reject",
                    actionIntent(ACTION_REJECT_JOIN, payload.userId, null, notificationId),
                )
                .build()
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }.onFailure { log(LogLevel.WARNING, "Failed to post join notification", it.message) }
    }

    private fun notifySuggestion(payload: SuggestionReceivedPayload) {
        if (!notificationsAllowed()) {
            log(LogLevel.WARNING, "Notification permission missing")
            return
        }
        runCatching {
            val notificationId = notificationIdsBySuggestion.getOrPut(payload.suggestionId) {
                (System.currentTimeMillis() xor payload.suggestionId.hashCode()).toInt()
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_headphones_generic)
                .setContentTitle("Track suggestion")
                .setContentText(
                    "${payload.fromUsername} suggested ${payload.trackInfo.title}",
                )
                .setAutoCancel(true)
                .addAction(
                    0,
                    "Approve",
                    actionIntent(
                        ACTION_APPROVE_SUGGESTION, null, payload.suggestionId, notificationId,
                    ),
                )
                .addAction(
                    0,
                    "Reject",
                    actionIntent(
                        ACTION_REJECT_SUGGESTION, null, payload.suggestionId, notificationId,
                    ),
                )
                .build()
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }.onFailure { log(LogLevel.WARNING, "Failed to post suggestion notification", it.message) }
    }

    private fun actionIntent(
        action: String,
        userId: String?,
        suggestionId: String?,
        notificationId: Int,
    ): PendingIntent {
        val intent = Intent(context, ListenTogetherActionReceiver::class.java).apply {
            this.action = action
            userId?.let { putExtra(EXTRA_USER_ID, it) }
            suggestionId?.let { putExtra(EXTRA_SUGGESTION_ID, it) }
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getBroadcast(
            context,
            notificationId xor action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelJoinNotification(userId: String) {
        notificationIdsByUser.remove(userId)?.let {
            NotificationManagerCompat.from(context).cancel(it)
        }
    }

    private fun cancelSuggestionNotification(suggestionId: String) {
        notificationIdsBySuggestion.remove(suggestionId)?.let {
            NotificationManagerCompat.from(context).cancel(it)
        }
    }

    private fun showToast(message: String) {
        scope.launch {
            runCatching {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ------------------------------------------------------------------ network / wakelock

    private fun registerNetworkObserver() {
        val manager = connectivityManager ?: return
        runCatching {
            manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val wasOffline = !networkAvailable
                    networkAvailable = true
                    if (wasOffline && (_connectionState.value == ConnectionState.DISCONNECTED ||
                            _connectionState.value == ConnectionState.ERROR) &&
                        hasReconnectContext()
                    ) {
                        reconnectAttempts = 0
                        connect()
                    }
                }

                override fun onLost(network: Network) {
                    networkAvailable = false
                    log(LogLevel.WARNING, "Network lost")
                    cancelPing()
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
            })
        }.onFailure { log(LogLevel.WARNING, "Cannot observe network", it.message) }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        runCatching {
            val power = context.getSystemService(PowerManager::class.java) ?: return
            val lock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            lock.setReferenceCounted(false)
            lock.acquire(10 * 60 * 1000L)
            wakeLock = lock
        }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }
}
