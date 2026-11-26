package com.revenge.music.listentogether

import com.google.protobuf.ByteString
import com.revenge.music.listentogether.proto.Listentogether
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A decoded frame, before payload interpretation. */
class DecodedMessage(
    val type: String,
    val format: MessageFormat,
    val compressed: Boolean,
    val payload: ByteArray,
)

/**
 * Envelope codec. Reads either framing; writes the framing currently negotiated with the server.
 * Unknown or unsupported payload types fall back to JSON so an upgrade to protobuf can never make
 * a later message unsendable.
 */
class MessageCodec(
    private val json: Json = DEFAULT_JSON,
) {
    @Volatile
    var outgoingFormat: MessageFormat = MessageFormat.JSON
        private set

    @Volatile
    var compressionEnabled: Boolean = false
        private set

    fun reset() {
        outgoingFormat = MessageFormat.JSON
        compressionEnabled = false
    }

    /** Called once the first protobuf frame is observed. */
    fun upgradeToProtobuf() {
        outgoingFormat = MessageFormat.PROTOBUF
        compressionEnabled = true
    }

    fun detectFormat(frame: ByteArray): MessageFormat {
        if (frame.isEmpty()) return MessageFormat.JSON
        return if (frame[0] == '{'.code.toByte()) MessageFormat.JSON else MessageFormat.PROTOBUF
    }

    fun encode(type: String, payload: Any?): ByteArray? = runCatching {
        val useProto =
            outgoingFormat == MessageFormat.PROTOBUF && type in MessageTypes.protobufTypes
        if (useProto) encodeProto(type, payload) else encodeJson(type, payload)
    }.getOrNull()

    private fun encodeProto(type: String, payload: Any?): ByteArray? {
        val payloadBytes = if (payload == null) ByteArray(0) else ProtoPayload.encode(type, payload)
            ?: return encodeJson(type, payload)

        var compressed = false
        var body = payloadBytes
        if (compressionEnabled && payloadBytes.size > COMPRESSION_THRESHOLD) {
            val gzip = gzip(payloadBytes)
            if (gzip.size < payloadBytes.size) {
                body = gzip
                compressed = true
            }
        }
        return Listentogether.Envelope.newBuilder()
            .setType(type)
            .setPayload(ByteString.copyFrom(body))
            .setCompressed(compressed)
            .build()
            .toByteArray()
    }

    private fun encodeJson(type: String, payload: Any?): ByteArray? {
        val element: JsonElement = if (payload == null) {
            JsonNull
        } else {
            val serializer = ProtoPayload.jsonSerializer(type) ?: return null
            @Suppress("UNCHECKED_CAST")
            json.encodeToJsonElement(serializer as SerializationStrategy<Any>, payload)
        }
        val text = buildJsonObject {
            put("type", type)
            put("payload", element)
        }.toString()
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (compressionEnabled && bytes.size > COMPRESSION_THRESHOLD) {
            val gzip = gzip(bytes)
            if (gzip.size < bytes.size) return gzip
        }
        return bytes
    }

    fun decode(frame: ByteArray): DecodedMessage? = runCatching {
        val (raw, wasCompressed) = if (isGzip(frame)) gunzip(frame) to true else frame to false
        when (detectFormat(raw)) {
            MessageFormat.JSON -> decodeJsonFrame(raw, wasCompressed)
            MessageFormat.PROTOBUF -> decodeProtoFrame(raw, wasCompressed)
        }
    }.getOrNull()

    private fun decodeJsonFrame(frame: ByteArray, wasCompressed: Boolean): DecodedMessage? {
        if (frame.isEmpty()) return null
        val root = json.parseToJsonElement(frame.toString(Charsets.UTF_8))
        val obj = root as? kotlinx.serialization.json.JsonObject ?: return null
        val type = (obj["type"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return null
        val payload = obj["payload"]
        val payloadBytes =
            if (payload == null || payload is JsonNull) ByteArray(0)
            else payload.toString().toByteArray(Charsets.UTF_8)
        return DecodedMessage(type, MessageFormat.JSON, wasCompressed, payloadBytes)
    }

    private fun decodeProtoFrame(frame: ByteArray, wasCompressed: Boolean): DecodedMessage? {
        val envelope = Listentogether.Envelope.parseFrom(frame)
        if (envelope.type.isEmpty()) return null
        var payload = envelope.payload.toByteArray()
        var compressed = envelope.compressed
        if (compressed) {
            payload = gunzip(payload)
            compressed = true
        }
        return DecodedMessage(envelope.type, MessageFormat.PROTOBUF, compressed, payload)
    }

    fun decodePayload(
        msgType: String,
        payload: ByteArray,
        format: MessageFormat = MessageFormat.PROTOBUF,
    ): Any? = runCatching {
        if (format == MessageFormat.JSON) decodeJsonPayload(msgType, payload)
        else ProtoPayload.decode(msgType, payload)
    }.getOrNull()

    private fun decodeJsonPayload(msgType: String, payload: ByteArray): Any? {
        if (payload.isEmpty()) return null
        val text = payload.toString(Charsets.UTF_8)
        if (text == "null") return null
        val serializer = ProtoPayload.jsonSerializer(msgType) ?: return null
        @Suppress("UNCHECKED_CAST")
        return json.decodeFromJsonElement(
            serializer as DeserializationStrategy<Any>,
            json.parseToJsonElement(text),
        )
    }

    private fun isGzip(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()

    private fun gzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    private fun gunzip(bytes: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }

    companion object {
        const val COMPRESSION_THRESHOLD = 100

        val DEFAULT_JSON: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
    }
}

/** Translation between serialisable models and the generated protobuf messages. */
internal object ProtoPayload {

    private val noPayloadTypes = setOf(
        MessageTypes.LEAVE_ROOM,
        MessageTypes.PING,
        MessageTypes.PONG,
        MessageTypes.REQUEST_SYNC,
    )

    fun jsonSerializer(type: String): KSerializer<*>? = when (type) {
        MessageTypes.CREATE_ROOM -> CreateRoomPayload.serializer()
        MessageTypes.JOIN_ROOM -> JoinRoomPayload.serializer()
        MessageTypes.APPROVE_JOIN -> ApproveJoinPayload.serializer()
        MessageTypes.REJECT_JOIN -> RejectJoinPayload.serializer()
        MessageTypes.PLAYBACK_ACTION -> PlaybackActionPayload.serializer()
        MessageTypes.BUFFER_READY -> BufferReadyPayload.serializer()
        MessageTypes.KICK_USER -> KickUserPayload.serializer()
        MessageTypes.TRANSFER_HOST -> TransferHostPayload.serializer()
        MessageTypes.CHAT -> ChatPayload.serializer()
        MessageTypes.RECONNECT -> ReconnectPayload.serializer()
        MessageTypes.SUGGEST_TRACK -> SuggestTrackPayload.serializer()
        MessageTypes.APPROVE_SUGGESTION -> ApproveSuggestionPayload.serializer()
        MessageTypes.REJECT_SUGGESTION -> RejectSuggestionPayload.serializer()
        MessageTypes.UPDATE_ROOM_SETTINGS -> UpdateRoomSettingsPayload.serializer()
        MessageTypes.ROOM_CREATED -> RoomCreatedPayload.serializer()
        MessageTypes.JOIN_REQUEST -> JoinRequestPayload.serializer()
        MessageTypes.JOIN_APPROVED -> JoinApprovedPayload.serializer()
        MessageTypes.JOIN_REJECTED -> JoinRejectedPayload.serializer()
        MessageTypes.USER_JOINED -> UserJoinedPayload.serializer()
        MessageTypes.USER_LEFT -> UserLeftPayload.serializer()
        MessageTypes.BUFFER_WAIT -> BufferWaitPayload.serializer()
        MessageTypes.BUFFER_COMPLETE -> BufferCompletePayload.serializer()
        MessageTypes.ERROR -> ErrorPayload.serializer()
        MessageTypes.CHAT_MESSAGE -> ChatMessagePayload.serializer()
        MessageTypes.HOST_CHANGED -> HostChangedPayload.serializer()
        MessageTypes.KICKED -> KickedPayload.serializer()
        MessageTypes.SYNC_PLAYBACK -> PlaybackActionPayload.serializer()
        MessageTypes.SYNC_STATE -> SyncStatePayload.serializer()
        MessageTypes.RECONNECTED -> ReconnectedPayload.serializer()
        MessageTypes.USER_RECONNECTED -> UserReconnectedPayload.serializer()
        MessageTypes.USER_DISCONNECTED -> UserDisconnectedPayload.serializer()
        MessageTypes.SUGGESTION_RECEIVED -> SuggestionReceivedPayload.serializer()
        MessageTypes.SUGGESTION_APPROVED -> SuggestionApprovedPayload.serializer()
        MessageTypes.SUGGESTION_REJECTED -> SuggestionRejectedPayload.serializer()
        MessageTypes.ROOM_SETTINGS_CHANGED -> UpdateRoomSettingsPayload.serializer()
        else -> null
    }

    /** Returns the serialised protobuf payload or null when the type has no protobuf definition. */
    fun encode(type: String, payload: Any): ByteArray? = when (type) {
        MessageTypes.CREATE_ROOM -> (payload as CreateRoomPayload).let {
            Listentogether.CreateRoomPayload.newBuilder().setUsername(it.username).build()
        }
        MessageTypes.JOIN_ROOM -> (payload as JoinRoomPayload).let {
            Listentogether.JoinRoomPayload.newBuilder()
                .setRoomCode(it.roomCode).setUsername(it.username).build()
        }
        MessageTypes.APPROVE_JOIN -> (payload as ApproveJoinPayload).let {
            Listentogether.ApproveJoinPayload.newBuilder().setUserId(it.userId).build()
        }
        MessageTypes.REJECT_JOIN -> (payload as RejectJoinPayload).let {
            Listentogether.RejectJoinPayload.newBuilder()
                .setUserId(it.userId).setReason(it.reason.orEmpty()).build()
        }
        MessageTypes.PLAYBACK_ACTION, MessageTypes.SYNC_PLAYBACK ->
            (payload as PlaybackActionPayload).toProto()
        MessageTypes.BUFFER_READY -> (payload as BufferReadyPayload).let {
            Listentogether.BufferReadyPayload.newBuilder().setTrackId(it.trackId).build()
        }
        MessageTypes.KICK_USER -> (payload as KickUserPayload).let {
            Listentogether.KickUserPayload.newBuilder()
                .setUserId(it.userId).setReason(it.reason.orEmpty()).build()
        }
        MessageTypes.TRANSFER_HOST -> (payload as TransferHostPayload).let {
            Listentogether.TransferHostPayload.newBuilder().setNewHostId(it.newHostId).build()
        }
        MessageTypes.RECONNECT -> (payload as ReconnectPayload).let {
            Listentogether.ReconnectPayload.newBuilder().setSessionToken(it.sessionToken).build()
        }
        MessageTypes.SUGGEST_TRACK -> (payload as SuggestTrackPayload).let {
            Listentogether.SuggestTrackPayload.newBuilder()
                .setTrackInfo(it.trackInfo.toProto()).build()
        }
        MessageTypes.APPROVE_SUGGESTION -> (payload as ApproveSuggestionPayload).let {
            Listentogether.ApproveSuggestionPayload.newBuilder()
                .setSuggestionId(it.suggestionId).build()
        }
        MessageTypes.REJECT_SUGGESTION -> (payload as RejectSuggestionPayload).let {
            Listentogether.RejectSuggestionPayload.newBuilder()
                .setSuggestionId(it.suggestionId).setReason(it.reason.orEmpty()).build()
        }
        MessageTypes.UPDATE_ROOM_SETTINGS -> (payload as UpdateRoomSettingsPayload).let {
            Listentogether.UpdateRoomSettingsPayload.newBuilder()
                .setAllowParticipantControl(it.allowParticipantControl).build()
        }
        MessageTypes.ROOM_CREATED -> (payload as RoomCreatedPayload).let {
            Listentogether.RoomCreatedPayload.newBuilder()
                .setRoomCode(it.roomCode).setUserId(it.userId)
                .setSessionToken(it.sessionToken).build()
        }
        MessageTypes.JOIN_REQUEST -> (payload as JoinRequestPayload).let {
            Listentogether.JoinRequestPayload.newBuilder()
                .setUserId(it.userId).setUsername(it.username).build()
        }
        MessageTypes.JOIN_APPROVED -> (payload as JoinApprovedPayload).let { p ->
            val builder = Listentogether.JoinApprovedPayload.newBuilder()
                .setRoomCode(p.roomCode).setUserId(p.userId)
                .setSessionToken(p.sessionToken.orEmpty())
            p.state?.let { builder.setState(it.toProto()) }
            builder.build()
        }
        MessageTypes.JOIN_REJECTED -> (payload as JoinRejectedPayload).let {
            Listentogether.JoinRejectedPayload.newBuilder().setReason(it.reason.orEmpty()).build()
        }
        MessageTypes.USER_JOINED -> (payload as UserJoinedPayload).let {
            Listentogether.UserJoinedPayload.newBuilder()
                .setUserId(it.userId).setUsername(it.username).build()
        }
        MessageTypes.USER_LEFT -> (payload as UserLeftPayload).let {
            Listentogether.UserLeftPayload.newBuilder()
                .setUserId(it.userId).setUsername(it.username).build()
        }
        MessageTypes.BUFFER_WAIT -> (payload as BufferWaitPayload).let {
            Listentogether.BufferWaitPayload.newBuilder()
                .setTrackId(it.trackId).addAllWaitingFor(it.waitingFor).build()
        }
        MessageTypes.BUFFER_COMPLETE -> (payload as BufferCompletePayload).let {
            Listentogether.BufferCompletePayload.newBuilder().setTrackId(it.trackId).build()
        }
        MessageTypes.ERROR -> (payload as ErrorPayload).let {
            Listentogether.ErrorPayload.newBuilder()
                .setCode(it.code).setMessage(it.message).build()
        }
        MessageTypes.HOST_CHANGED -> (payload as HostChangedPayload).let {
            Listentogether.HostChangedPayload.newBuilder()
                .setNewHostId(it.newHostId).setNewHostName(it.newHostName).build()
        }
        MessageTypes.KICKED -> (payload as KickedPayload).let {
            Listentogether.KickedPayload.newBuilder().setReason(it.reason.orEmpty()).build()
        }
        MessageTypes.SYNC_STATE -> (payload as SyncStatePayload).toProto()
        MessageTypes.RECONNECTED -> (payload as ReconnectedPayload).let { p ->
            val builder = Listentogether.ReconnectedPayload.newBuilder()
                .setRoomCode(p.roomCode).setUserId(p.userId)
                .setIsHost(p.isHost)
            p.state?.let { builder.setState(it.toProto()) }
            builder.build()
        }
        MessageTypes.USER_RECONNECTED -> (payload as UserReconnectedPayload).let {
            Listentogether.UserReconnectedPayload.newBuilder()
                .setUserId(it.userId).setUsername(it.username).build()
        }
        MessageTypes.USER_DISCONNECTED -> (payload as UserDisconnectedPayload).let {
            Listentogether.UserDisconnectedPayload.newBuilder()
                .setUserId(it.userId).setUsername(it.username).build()
        }
        MessageTypes.SUGGESTION_RECEIVED -> (payload as SuggestionReceivedPayload).let {
            Listentogether.SuggestionReceivedPayload.newBuilder()
                .setSuggestionId(it.suggestionId).setFromUserId(it.fromUserId)
                .setFromUsername(it.fromUsername).setTrackInfo(it.trackInfo.toProto()).build()
        }
        MessageTypes.SUGGESTION_APPROVED -> (payload as SuggestionApprovedPayload).let { p ->
            val builder = Listentogether.SuggestionApprovedPayload.newBuilder()
                .setSuggestionId(p.suggestionId)
            p.trackInfo?.let { builder.setTrackInfo(it.toProto()) }
            builder.build()
        }
        MessageTypes.SUGGESTION_REJECTED -> (payload as SuggestionRejectedPayload).let {
            Listentogether.SuggestionRejectedPayload.newBuilder()
                .setSuggestionId(it.suggestionId).setReason(it.reason.orEmpty()).build()
        }
        MessageTypes.ROOM_SETTINGS_CHANGED -> (payload as UpdateRoomSettingsPayload).let {
            Listentogether.UpdateRoomSettingsPayload.newBuilder()
                .setAllowParticipantControl(it.allowParticipantControl).build()
        }
        else -> null
    }?.toByteArray()

    fun decode(type: String, bytes: ByteArray): Any? = when (type) {
        MessageTypes.CREATE_ROOM ->
            Listentogether.CreateRoomPayload.parseFrom(bytes).let { CreateRoomPayload(it.username) }
        MessageTypes.JOIN_ROOM -> Listentogether.JoinRoomPayload.parseFrom(bytes).let {
            JoinRoomPayload(it.roomCode, it.username)
        }
        MessageTypes.APPROVE_JOIN ->
            Listentogether.ApproveJoinPayload.parseFrom(bytes).let { ApproveJoinPayload(it.userId) }
        MessageTypes.REJECT_JOIN -> Listentogether.RejectJoinPayload.parseFrom(bytes).let {
            RejectJoinPayload(it.userId, it.reason.ifEmpty { null })
        }
        MessageTypes.PLAYBACK_ACTION, MessageTypes.SYNC_PLAYBACK ->
            Listentogether.PlaybackActionPayload.parseFrom(bytes).toModel()
        MessageTypes.BUFFER_READY ->
            Listentogether.BufferReadyPayload.parseFrom(bytes).let { BufferReadyPayload(it.trackId) }
        MessageTypes.KICK_USER -> Listentogether.KickUserPayload.parseFrom(bytes).let {
            KickUserPayload(it.userId, it.reason.ifEmpty { null })
        }
        MessageTypes.TRANSFER_HOST ->
            Listentogether.TransferHostPayload.parseFrom(bytes).let { TransferHostPayload(it.newHostId) }
        MessageTypes.RECONNECT ->
            Listentogether.ReconnectPayload.parseFrom(bytes).let { ReconnectPayload(it.sessionToken) }
        MessageTypes.SUGGEST_TRACK ->
            Listentogether.SuggestTrackPayload.parseFrom(bytes).let {
                SuggestTrackPayload(it.trackInfo.toModel())
            }
        MessageTypes.APPROVE_SUGGESTION ->
            Listentogether.ApproveSuggestionPayload.parseFrom(bytes).let {
                ApproveSuggestionPayload(it.suggestionId)
            }
        MessageTypes.REJECT_SUGGESTION ->
            Listentogether.RejectSuggestionPayload.parseFrom(bytes).let {
                RejectSuggestionPayload(it.suggestionId, it.reason.ifEmpty { null })
            }
        MessageTypes.UPDATE_ROOM_SETTINGS ->
            Listentogether.UpdateRoomSettingsPayload.parseFrom(bytes).let {
                UpdateRoomSettingsPayload(it.allowParticipantControl)
            }
        MessageTypes.ROOM_CREATED -> Listentogether.RoomCreatedPayload.parseFrom(bytes).let {
            RoomCreatedPayload(it.roomCode, it.userId, it.sessionToken)
        }
        MessageTypes.JOIN_REQUEST -> Listentogether.JoinRequestPayload.parseFrom(bytes).let {
            JoinRequestPayload(it.userId, it.username)
        }
        MessageTypes.JOIN_APPROVED -> Listentogether.JoinApprovedPayload.parseFrom(bytes).let {
            JoinApprovedPayload(
                it.roomCode,
                it.userId,
                it.sessionToken.ifEmpty { null },
                if (it.hasState()) it.state.toModel() else null,
            )
        }
        MessageTypes.JOIN_REJECTED ->
            Listentogether.JoinRejectedPayload.parseFrom(bytes).let {
                JoinRejectedPayload(it.reason.ifEmpty { null })
            }
        MessageTypes.USER_JOINED -> Listentogether.UserJoinedPayload.parseFrom(bytes).let {
            UserJoinedPayload(it.userId, it.username)
        }
        MessageTypes.USER_LEFT -> Listentogether.UserLeftPayload.parseFrom(bytes).let {
            UserLeftPayload(it.userId, it.username)
        }
        MessageTypes.BUFFER_WAIT -> Listentogether.BufferWaitPayload.parseFrom(bytes).let {
            BufferWaitPayload(it.trackId, it.waitingForList)
        }
        MessageTypes.BUFFER_COMPLETE ->
            Listentogether.BufferCompletePayload.parseFrom(bytes).let {
                BufferCompletePayload(it.trackId)
            }
        MessageTypes.ERROR -> Listentogether.ErrorPayload.parseFrom(bytes).let {
            ErrorPayload(it.code, it.message)
        }
        MessageTypes.HOST_CHANGED -> Listentogether.HostChangedPayload.parseFrom(bytes).let {
            HostChangedPayload(it.newHostId, it.newHostName)
        }
        MessageTypes.KICKED ->
            Listentogether.KickedPayload.parseFrom(bytes).let { KickedPayload(it.reason.ifEmpty { null }) }
        MessageTypes.SYNC_STATE -> Listentogether.SyncStatePayload.parseFrom(bytes).toModel()
        MessageTypes.RECONNECTED -> Listentogether.ReconnectedPayload.parseFrom(bytes).let {
            ReconnectedPayload(
                it.roomCode,
                it.userId,
                if (it.hasState()) it.state.toModel() else null,
                it.isHost,
            )
        }
        MessageTypes.USER_RECONNECTED ->
            Listentogether.UserReconnectedPayload.parseFrom(bytes).let {
                UserReconnectedPayload(it.userId, it.username)
            }
        MessageTypes.USER_DISCONNECTED ->
            Listentogether.UserDisconnectedPayload.parseFrom(bytes).let {
                UserDisconnectedPayload(it.userId, it.username)
            }
        MessageTypes.SUGGESTION_RECEIVED ->
            Listentogether.SuggestionReceivedPayload.parseFrom(bytes).let {
                SuggestionReceivedPayload(
                    it.suggestionId, it.fromUserId, it.fromUsername, it.trackInfo.toModel(),
                )
            }
        MessageTypes.SUGGESTION_APPROVED ->
            Listentogether.SuggestionApprovedPayload.parseFrom(bytes).let {
                SuggestionApprovedPayload(
                    it.suggestionId,
                    if (it.hasTrackInfo()) it.trackInfo.toModel() else null,
                )
            }
        MessageTypes.SUGGESTION_REJECTED ->
            Listentogether.SuggestionRejectedPayload.parseFrom(bytes).let {
                SuggestionRejectedPayload(it.suggestionId, it.reason.ifEmpty { null })
            }
        MessageTypes.ROOM_SETTINGS_CHANGED ->
            Listentogether.UpdateRoomSettingsPayload.parseFrom(bytes).let {
                UpdateRoomSettingsPayload(it.allowParticipantControl)
            }
        else -> if (type in noPayloadTypes) null else null
    }
}

private fun TrackInfo.toProto(): Listentogether.TrackInfo =
    Listentogether.TrackInfo.newBuilder()
        .setId(id)
        .setTitle(title)
        .setArtist(artist)
        .setAlbum(album.orEmpty())
        .setDuration(duration)
        .setThumbnail(thumbnail.orEmpty())
        .setSuggestedBy(suggestedBy.orEmpty())
        .build()

private fun Listentogether.TrackInfo.toModel(): TrackInfo =
    TrackInfo(
        id = id,
        title = title,
        artist = artist,
        album = album.ifEmpty { null },
        duration = duration,
        thumbnail = thumbnail.ifEmpty { null },
        suggestedBy = suggestedBy.ifEmpty { null },
    )

private fun UserInfo.toProto(): Listentogether.UserInfo =
    Listentogether.UserInfo.newBuilder()
        .setUserId(userId)
        .setUsername(username)
        .setIsHost(isHost)
        .setIsConnected(isConnected)
        .build()

private fun Listentogether.UserInfo.toModel(): UserInfo =
    UserInfo(userId, username, isHost, isConnected)

private fun RoomState.toProto(): Listentogether.RoomState {
    val builder = Listentogether.RoomState.newBuilder()
        .setRoomCode(roomCode)
        .setHostId(hostId)
        .addAllUsers(users.map { it.toProto() })
        .setIsPlaying(isPlaying)
        .setPosition(position)
        .setLastUpdate(lastUpdate)
        .setVolume(volume)
        .addAllQueue(queue.map { it.toProto() })
        .setAllowParticipantControl(allowParticipantControl)
    currentTrack?.let { builder.setCurrentTrack(it.toProto()) }
    return builder.build()
}

private fun Listentogether.RoomState.toModel(): RoomState =
    RoomState(
        roomCode = roomCode,
        hostId = hostId,
        users = usersList.map { it.toModel() },
        currentTrack = if (hasCurrentTrack()) currentTrack.toModel() else null,
        isPlaying = isPlaying,
        position = position,
        lastUpdate = lastUpdate,
        volume = if (volume <= 0f) 1f else volume,
        queue = queueList.map { it.toModel() },
        allowParticipantControl = allowParticipantControl,
    )

private fun PlaybackActionPayload.toProto(): Listentogether.PlaybackActionPayload {
    val builder = Listentogether.PlaybackActionPayload.newBuilder()
        .setAction(action)
        .setTrackId(trackId.orEmpty())
        .setPosition(position ?: 0L)
        .setInsertNext(insertNext)
        .addAllQueue(queue.map { it.toProto() })
        .setQueueTitle(queueTitle.orEmpty())
        .setVolume(volume ?: 0f)
        .setServerTime(serverTime ?: 0L)
    trackInfo?.let { builder.setTrackInfo(it.toProto()) }
    return builder.build()
}

private fun Listentogether.PlaybackActionPayload.toModel(): PlaybackActionPayload =
    PlaybackActionPayload(
        action = action,
        trackId = trackId.ifEmpty { null },
        position = position.takeIf { it > 0L },
        trackInfo = if (hasTrackInfo()) trackInfo.toModel() else null,
        insertNext = insertNext,
        queue = queueList.map { it.toModel() },
        queueTitle = queueTitle.ifEmpty { null },
        volume = volume.takeIf { it > 0f },
        serverTime = serverTime.takeIf { it > 0L },
    )

private fun SyncStatePayload.toProto(): Listentogether.SyncStatePayload {
    val builder = Listentogether.SyncStatePayload.newBuilder()
        .setIsPlaying(isPlaying)
        .setPosition(position ?: 0L)
        .setLastUpdate(lastUpdate)
        .addAllQueue(queue.orEmpty().map { it.toProto() })
        .setVolume(volume ?: 0f)
    currentTrack?.let { builder.setCurrentTrack(it.toProto()) }
    return builder.build()
}

private fun Listentogether.SyncStatePayload.toModel(): SyncStatePayload =
    SyncStatePayload(
        currentTrack = if (hasCurrentTrack()) currentTrack.toModel() else null,
        isPlaying = isPlaying,
        position = position.takeIf { it > 0L },
        lastUpdate = lastUpdate,
        queue = if (queueList.isEmpty()) null else queueList.map { it.toModel() },
        volume = volume.takeIf { it > 0f },
    )
