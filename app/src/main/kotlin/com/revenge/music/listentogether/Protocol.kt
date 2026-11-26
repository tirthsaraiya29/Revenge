package com.revenge.music.listentogether

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire format currently used for outgoing frames. */
enum class MessageFormat { JSON, PROTOBUF }

/** Exact message type strings exchanged with the relay server. */
object MessageTypes {
    // Client -> server
    const val CREATE_ROOM = "create_room"
    const val JOIN_ROOM = "join_room"
    const val LEAVE_ROOM = "leave_room"
    const val APPROVE_JOIN = "approve_join"
    const val REJECT_JOIN = "reject_join"
    const val PLAYBACK_ACTION = "playback_action"
    const val BUFFER_READY = "buffer_ready"
    const val KICK_USER = "kick_user"
    const val TRANSFER_HOST = "transfer_host"
    const val PING = "ping"
    const val CHAT = "chat"
    const val REQUEST_SYNC = "request_sync"
    const val RECONNECT = "reconnect"
    const val SUGGEST_TRACK = "suggest_track"
    const val APPROVE_SUGGESTION = "approve_suggestion"
    const val REJECT_SUGGESTION = "reject_suggestion"
    const val UPDATE_ROOM_SETTINGS = "update_room_settings"

    // Server -> client
    const val ROOM_CREATED = "room_created"
    const val JOIN_REQUEST = "join_request"
    const val JOIN_APPROVED = "join_approved"
    const val JOIN_REJECTED = "join_rejected"
    const val USER_JOINED = "user_joined"
    const val USER_LEFT = "user_left"
    const val SYNC_PLAYBACK = "sync_playback"
    const val BUFFER_WAIT = "buffer_wait"
    const val BUFFER_COMPLETE = "buffer_complete"
    const val ERROR = "error"
    const val PONG = "pong"
    const val HOST_CHANGED = "host_changed"
    const val KICKED = "kicked"
    const val SYNC_STATE = "sync_state"
    const val RECONNECTED = "reconnected"
    const val USER_RECONNECTED = "user_reconnected"
    const val USER_DISCONNECTED = "user_disconnected"
    const val SUGGESTION_RECEIVED = "suggestion_received"
    const val SUGGESTION_APPROVED = "suggestion_approved"
    const val SUGGESTION_REJECTED = "suggestion_rejected"
    const val CHAT_MESSAGE = "chat"
    const val ROOM_SETTINGS_CHANGED = "room_settings_changed"

    val protobufTypes: Set<String> = setOf(
        CREATE_ROOM, JOIN_ROOM, LEAVE_ROOM, APPROVE_JOIN, REJECT_JOIN, PLAYBACK_ACTION,
        BUFFER_READY, KICK_USER, TRANSFER_HOST, PING, REQUEST_SYNC, RECONNECT,
        SUGGEST_TRACK, APPROVE_SUGGESTION, REJECT_SUGGESTION, UPDATE_ROOM_SETTINGS,
        ROOM_CREATED, JOIN_REQUEST, JOIN_APPROVED, JOIN_REJECTED, USER_JOINED, USER_LEFT,
        SYNC_PLAYBACK, BUFFER_WAIT, BUFFER_COMPLETE, ERROR, PONG, HOST_CHANGED, KICKED,
        SYNC_STATE, RECONNECTED, USER_RECONNECTED, USER_DISCONNECTED, SUGGESTION_RECEIVED,
        SUGGESTION_APPROVED, SUGGESTION_REJECTED, ROOM_SETTINGS_CHANGED,
    )
}

/** Playback action string values carried by [PlaybackActionPayload.action]. */
object PlaybackActions {
    const val PLAY = "play"
    const val PAUSE = "pause"
    const val SEEK = "seek"
    const val SKIP_NEXT = "skip_next"
    const val SKIP_PREV = "skip_prev"
    const val CHANGE_TRACK = "change_track"
    const val QUEUE_ADD = "queue_add"
    const val QUEUE_REMOVE = "queue_remove"
    const val QUEUE_CLEAR = "queue_clear"
    const val SYNC_QUEUE = "sync_queue"
    const val SET_VOLUME = "set_volume"
}

@Serializable
data class TrackInfo(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val duration: Long = 0L,
    val thumbnail: String? = null,
    @SerialName("suggested_by") val suggestedBy: String? = null,
)

@Serializable
data class UserInfo(
    @SerialName("user_id") val userId: String,
    val username: String,
    @SerialName("is_host") val isHost: Boolean = false,
    @SerialName("is_connected") val isConnected: Boolean = true,
)

@Serializable
data class RoomState(
    @SerialName("room_code") val roomCode: String,
    @SerialName("host_id") val hostId: String,
    val users: List<UserInfo> = emptyList(),
    @SerialName("current_track") val currentTrack: TrackInfo? = null,
    @SerialName("is_playing") val isPlaying: Boolean = false,
    val position: Long = 0L,
    @SerialName("last_update") val lastUpdate: Long = 0L,
    val volume: Float = 1f,
    val queue: List<TrackInfo> = emptyList(),
    @SerialName("allow_participant_control") val allowParticipantControl: Boolean = false,
)

@Serializable
data class UpdateRoomSettingsPayload(
    @SerialName("allow_participant_control") val allowParticipantControl: Boolean,
)

@Serializable
data class CreateRoomPayload(val username: String)

@Serializable
data class JoinRoomPayload(
    @SerialName("room_code") val roomCode: String,
    val username: String,
)

@Serializable
data class ApproveJoinPayload(@SerialName("user_id") val userId: String)

@Serializable
data class RejectJoinPayload(
    @SerialName("user_id") val userId: String,
    val reason: String? = null,
)

@Serializable
data class PlaybackActionPayload(
    val action: String,
    @SerialName("track_id") val trackId: String? = null,
    val position: Long? = null,
    @SerialName("track_info") val trackInfo: TrackInfo? = null,
    @SerialName("insert_next") val insertNext: Boolean = false,
    val queue: List<TrackInfo> = emptyList(),
    @SerialName("queue_title") val queueTitle: String? = null,
    val volume: Float? = null,
    @SerialName("server_time") val serverTime: Long? = null,
)

@Serializable
data class BufferReadyPayload(@SerialName("track_id") val trackId: String)

@Serializable
data class KickUserPayload(
    @SerialName("user_id") val userId: String,
    val reason: String? = null,
)

@Serializable
data class TransferHostPayload(@SerialName("new_host_id") val newHostId: String)

@Serializable
data class ChatPayload(
    val message: String,
    @SerialName("reply_to") val replyTo: RepliedMessage? = null,
)

@Serializable
data class RepliedMessage(
    val username: String,
    val message: String,
)

@Serializable
data class SuggestTrackPayload(@SerialName("track_info") val trackInfo: TrackInfo)

@Serializable
data class SuggestionReceivedPayload(
    @SerialName("suggestion_id") val suggestionId: String,
    @SerialName("from_user_id") val fromUserId: String,
    @SerialName("from_username") val fromUsername: String,
    @SerialName("track_info") val trackInfo: TrackInfo,
)

@Serializable
data class ApproveSuggestionPayload(@SerialName("suggestion_id") val suggestionId: String)

@Serializable
data class RejectSuggestionPayload(
    @SerialName("suggestion_id") val suggestionId: String,
    val reason: String? = null,
)

@Serializable
data class SuggestionApprovedPayload(
    @SerialName("suggestion_id") val suggestionId: String,
    @SerialName("track_info") val trackInfo: TrackInfo? = null,
)

@Serializable
data class SuggestionRejectedPayload(
    @SerialName("suggestion_id") val suggestionId: String,
    val reason: String? = null,
)

@Serializable
data class RoomCreatedPayload(
    @SerialName("room_code") val roomCode: String,
    @SerialName("user_id") val userId: String,
    @SerialName("session_token") val sessionToken: String,
)

@Serializable
data class JoinRequestPayload(
    @SerialName("user_id") val userId: String,
    val username: String,
)

@Serializable
data class JoinApprovedPayload(
    @SerialName("room_code") val roomCode: String,
    @SerialName("user_id") val userId: String,
    @SerialName("session_token") val sessionToken: String? = null,
    val state: RoomState? = null,
)

@Serializable
data class JoinRejectedPayload(val reason: String? = null)

@Serializable
data class UserJoinedPayload(
    @SerialName("user_id") val userId: String,
    val username: String,
)

@Serializable
data class UserLeftPayload(
    @SerialName("user_id") val userId: String,
    val username: String,
)

@Serializable
data class BufferWaitPayload(
    @SerialName("track_id") val trackId: String,
    @SerialName("waiting_for") val waitingFor: List<String> = emptyList(),
)

@Serializable
data class BufferCompletePayload(@SerialName("track_id") val trackId: String)

@Serializable
data class ErrorPayload(
    val code: String,
    val message: String,
)

@Serializable
data class ChatMessagePayload(
    @SerialName("user_id") val userId: String,
    val username: String,
    val message: String,
    val timestamp: Long,
    @SerialName("reply_to") val replyTo: RepliedMessage? = null,
)

@Serializable
data class HostChangedPayload(
    @SerialName("new_host_id") val newHostId: String,
    @SerialName("new_host_name") val newHostName: String,
)

@Serializable
data class KickedPayload(val reason: String? = null)

@Serializable
data class SyncStatePayload(
    @SerialName("current_track") val currentTrack: TrackInfo? = null,
    @SerialName("is_playing") val isPlaying: Boolean = false,
    val position: Long? = null,
    @SerialName("last_update") val lastUpdate: Long = 0L,
    val queue: List<TrackInfo>? = null,
    val volume: Float? = null,
)

@Serializable
data class ReconnectPayload(@SerialName("session_token") val sessionToken: String)

@Serializable
data class ReconnectedPayload(
    @SerialName("room_code") val roomCode: String,
    @SerialName("user_id") val userId: String,
    val state: RoomState? = null,
    @SerialName("is_host") val isHost: Boolean = false,
)

@Serializable
data class UserReconnectedPayload(
    @SerialName("user_id") val userId: String,
    val username: String,
)

@Serializable
data class UserDisconnectedPayload(
    @SerialName("user_id") val userId: String,
    val username: String,
)
