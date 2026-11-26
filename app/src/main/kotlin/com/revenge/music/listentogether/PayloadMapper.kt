package com.revenge.music.listentogether

import com.revenge.music.listentogether.proto.Listentogether as Proto
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Maps the JSON payload representation used by the rest of the client to the typed protobuf
 * messages in [Proto] and back. Types without a proto definition (chat) return null, letting
 * [MessageCodec] fall back to JSON framing for that message.
 *
 * Optional values follow the wire contract: nullable strings use empty string, and optional
 * numbers use `<= 0` for absent.
 */
internal object PayloadMapper {

  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
  }

  fun encode(type: String, payloadJson: String): ByteArray? =
    runCatching { encodeTyped(type, payloadJson) }.getOrNull()

  fun decode(type: String, bytes: ByteArray): String? =
    if (bytes.isEmpty()) null else runCatching { decodeTyped(type, bytes) }.getOrNull()

  private fun <T> parse(payloadJson: String, serializer: KSerializer<T>): T =
    json.decodeFromString(serializer, payloadJson)

  private fun encodeTyped(type: String, payloadJson: String): ByteArray? =
    when (type) {
      MessageType.CREATE_ROOM ->
        Proto.CreateRoomPayload.newBuilder()
          .setUsername(parse(payloadJson, CreateRoomPayload.serializer()).username)
          .build()
          .toByteArray()
      MessageType.JOIN_ROOM -> {
        val m = parse(payloadJson, JoinRoomPayload.serializer())
        Proto.JoinRoomPayload.newBuilder()
          .setRoomCode(m.roomCode)
          .setUsername(m.username)
          .build()
          .toByteArray()
      }
      MessageType.APPROVE_JOIN ->
        Proto.ApproveJoinPayload.newBuilder()
          .setUserId(parse(payloadJson, ApproveJoinPayload.serializer()).userId)
          .build()
          .toByteArray()
      MessageType.REJECT_JOIN -> {
        val m = parse(payloadJson, RejectJoinPayload.serializer())
        Proto.RejectJoinPayload.newBuilder()
          .setUserId(m.userId)
          .setReason(m.reason.orEmptyString())
          .build()
          .toByteArray()
      }
      MessageType.PLAYBACK_ACTION -> {
        val m = parse(payloadJson, PlaybackActionPayload.serializer())
        Proto.PlaybackActionPayload.newBuilder()
          .setAction(m.action)
          .setTrackId(m.trackId.orEmptyString())
          .setPosition(m.position.orZero())
          .apply { m.trackInfo?.let { setTrackInfo(it.toProto()) } }
          .setInsertNext(m.insertNext)
          .addAllQueue(m.queue.map { it.toProto() })
          .setQueueTitle(m.queueTitle.orEmptyString())
          .setVolume(m.volume.orZero())
          .setServerTime(m.serverTime.orZero())
          .build()
          .toByteArray()
      }
      MessageType.BUFFER_READY ->
        Proto.BufferReadyPayload.newBuilder()
          .setTrackId(parse(payloadJson, BufferReadyPayload.serializer()).trackId)
          .build()
          .toByteArray()
      MessageType.KICK_USER -> {
        val m = parse(payloadJson, KickUserPayload.serializer())
        Proto.KickUserPayload.newBuilder()
          .setUserId(m.userId)
          .setReason(m.reason.orEmptyString())
          .build()
          .toByteArray()
      }
      MessageType.TRANSFER_HOST ->
        Proto.TransferHostPayload.newBuilder()
          .setNewHostId(parse(payloadJson, TransferHostPayload.serializer()).newHostId)
          .build()
          .toByteArray()
      MessageType.SUGGEST_TRACK ->
        Proto.SuggestTrackPayload.newBuilder()
          .setTrackInfo(parse(payloadJson, SuggestTrackPayload.serializer()).trackInfo.toProto())
          .build()
          .toByteArray()
      MessageType.APPROVE_SUGGESTION ->
        Proto.ApproveSuggestionPayload.newBuilder()
          .setSuggestionId(parse(payloadJson, ApproveSuggestionPayload.serializer()).suggestionId)
          .build()
          .toByteArray()
      MessageType.REJECT_SUGGESTION -> {
        val m = parse(payloadJson, RejectSuggestionPayload.serializer())
        Proto.RejectSuggestionPayload.newBuilder()
          .setSuggestionId(m.suggestionId)
          .setReason(m.reason.orEmptyString())
          .build()
          .toByteArray()
      }
      MessageType.UPDATE_ROOM_SETTINGS ->
        Proto.UpdateRoomSettingsPayload.newBuilder()
          .setAllowParticipantControl(
            parse(payloadJson, UpdateRoomSettingsPayload.serializer()).allowParticipantControl
          )
          .build()
          .toByteArray()
      MessageType.ROOM_CREATED -> {
        val m = parse(payloadJson, RoomCreatedPayload.serializer())
        Proto.RoomCreatedPayload.newBuilder()
          .setRoomCode(m.roomCode)
          .setUserId(m.userId)
          .setSessionToken(m.sessionToken)
          .build()
          .toByteArray()
      }
      MessageType.JOIN_REQUEST -> {
        val m = parse(payloadJson, JoinRequestPayload.serializer())
        Proto.JoinRequestPayload.newBuilder()
          .setUserId(m.userId)
          .setUsername(m.username)
          .build()
          .toByteArray()
      }
      MessageType.JOIN_APPROVED -> {
        val m = parse(payloadJson, JoinApprovedPayload.serializer())
        Proto.JoinApprovedPayload.newBuilder()
          .setRoomCode(m.roomCode)
          .setUserId(m.userId)
          .setSessionToken(m.sessionToken)
          .apply { m.state?.let { setState(it.toProto()) } }
          .build()
          .toByteArray()
      }
      MessageType.JOIN_REJECTED ->
        Proto.JoinRejectedPayload.newBuilder()
          .setReason(parse(payloadJson, JoinRejectedPayload.serializer()).reason.orEmptyString())
          .build()
          .toByteArray()
      MessageType.USER_JOINED -> {
        val m = parse(payloadJson, UserJoinedPayload.serializer())
        Proto.UserJoinedPayload.newBuilder()
          .setUserId(m.userId)
          .setUsername(m.username)
          .build()
          .toByteArray()
      }
      MessageType.USER_LEFT -> {
        val m = parse(payloadJson, UserLeftPayload.serializer())
        Proto.UserLeftPayload.newBuilder()
          .setUserId(m.userId)
          .setUsername(m.username)
          .build()
          .toByteArray()
      }
      MessageType.BUFFER_WAIT -> {
        val m = parse(payloadJson, BufferWaitPayload.serializer())
        Proto.BufferWaitPayload.newBuilder()
          .setTrackId(m.trackId)
          .addAllWaitingFor(m.waitingFor)
          .build()
          .toByteArray()
      }
      MessageType.BUFFER_COMPLETE ->
        Proto.BufferCompletePayload.newBuilder()
          .setTrackId(parse(payloadJson, BufferCompletePayload.serializer()).trackId)
          .build()
          .toByteArray()
      MessageType.ERROR -> {
        val m = parse(payloadJson, ErrorPayload.serializer())
        Proto.ErrorPayload.newBuilder()
          .setCode(m.code)
          .setMessage(m.message)
          .build()
          .toByteArray()
      }
      MessageType.HOST_CHANGED -> {
        val m = parse(payloadJson, HostChangedPayload.serializer())
        Proto.HostChangedPayload.newBuilder()
          .setNewHostId(m.newHostId)
          .setNewHostName(m.newHostName)
          .build()
          .toByteArray()
      }
      MessageType.KICKED ->
        Proto.KickedPayload.newBuilder()
          .setReason(parse(payloadJson, KickedPayload.serializer()).reason.orEmptyString())
          .build()
          .toByteArray()
      MessageType.SYNC_STATE -> {
        val m = parse(payloadJson, SyncStatePayload.serializer())
        Proto.SyncStatePayload.newBuilder()
          .apply { m.currentTrack?.let { setCurrentTrack(it.toProto()) } }
          .setIsPlaying(m.isPlaying)
          .setPosition(m.position.orZero())
          .setLastUpdate(m.lastUpdate)
          .addAllQueue(m.queue.orEmpty().map { it.toProto() })
          .setVolume(m.volume.orZero())
          .build()
          .toByteArray()
      }
      MessageType.RECONNECT ->
        Proto.ReconnectPayload.newBuilder()
          .setSessionToken(parse(payloadJson, ReconnectPayload.serializer()).sessionToken)
          .build()
          .toByteArray()
      MessageType.RECONNECTED -> {
        val m = parse(payloadJson, ReconnectedPayload.serializer())
        Proto.ReconnectedPayload.newBuilder()
          .setRoomCode(m.roomCode)
          .setUserId(m.userId)
          .apply { m.state?.let { setState(it.toProto()) } }
          .setIsHost(m.isHost)
          .build()
          .toByteArray()
      }
      MessageType.USER_RECONNECTED -> {
        val m = parse(payloadJson, UserReconnectedPayload.serializer())
        Proto.UserReconnectedPayload.newBuilder()
          .setUserId(m.userId)
          .setUsername(m.username)
          .build()
          .toByteArray()
      }
      MessageType.USER_DISCONNECTED -> {
        val m = parse(payloadJson, UserDisconnectedPayload.serializer())
        Proto.UserDisconnectedPayload.newBuilder()
          .setUserId(m.userId)
          .setUsername(m.username)
          .build()
          .toByteArray()
      }
      MessageType.SUGGESTION_RECEIVED -> {
        val m = parse(payloadJson, SuggestionReceivedPayload.serializer())
        Proto.SuggestionReceivedPayload.newBuilder()
          .setSuggestionId(m.suggestionId)
          .setFromUserId(m.fromUserId)
          .setFromUsername(m.fromUsername)
          .setTrackInfo(m.trackInfo.toProto())
          .build()
          .toByteArray()
      }
      MessageType.SUGGESTION_APPROVED -> {
        val m = parse(payloadJson, SuggestionApprovedPayload.serializer())
        Proto.SuggestionApprovedPayload.newBuilder()
          .setSuggestionId(m.suggestionId)
          .setTrackInfo(m.trackInfo.toProto())
          .build()
          .toByteArray()
      }
      MessageType.SUGGESTION_REJECTED -> {
        val m = parse(payloadJson, SuggestionRejectedPayload.serializer())
        Proto.SuggestionRejectedPayload.newBuilder()
          .setSuggestionId(m.suggestionId)
          .setReason(m.reason.orEmptyString())
          .build()
          .toByteArray()
      }
      MessageType.CHAT -> null
      else -> null
    }

  private fun decodeTyped(type: String, bytes: ByteArray): String? =
    when (type) {
      MessageType.CREATE_ROOM ->
        encodeModel(
          Proto.CreateRoomPayload.parseFrom(bytes).let { CreateRoomPayload(it.username) },
          CreateRoomPayload.serializer(),
        )
      MessageType.JOIN_ROOM ->
        encodeModel(
          Proto.JoinRoomPayload.parseFrom(bytes).let { JoinRoomPayload(it.roomCode, it.username) },
          JoinRoomPayload.serializer(),
        )
      MessageType.APPROVE_JOIN ->
        encodeModel(
          Proto.ApproveJoinPayload.parseFrom(bytes).let { ApproveJoinPayload(it.userId) },
          ApproveJoinPayload.serializer(),
        )
      MessageType.REJECT_JOIN ->
        encodeModel(
          Proto.RejectJoinPayload.parseFrom(bytes).let {
            RejectJoinPayload(it.userId, it.reason.nullIfEmpty())
          },
          RejectJoinPayload.serializer(),
        )
      MessageType.PLAYBACK_ACTION ->
        encodeModel(
          Proto.PlaybackActionPayload.parseFrom(bytes).let {
            PlaybackActionPayload(
              action = it.action,
              trackId = it.trackId.nullIfEmpty(),
              position = it.position.nullIfNonPositive(),
              trackInfo = if (it.hasTrackInfo()) it.trackInfo.toModel() else null,
              insertNext = it.insertNext,
              queue = it.queueList.map { track -> track.toModel() },
              queueTitle = it.queueTitle.nullIfEmpty(),
              volume = it.volume.nullIfNonPositive(),
              serverTime = it.serverTime.nullIfNonPositive(),
            )
          },
          PlaybackActionPayload.serializer(),
        )
      MessageType.BUFFER_READY ->
        encodeModel(
          Proto.BufferReadyPayload.parseFrom(bytes).let { BufferReadyPayload(it.trackId) },
          BufferReadyPayload.serializer(),
        )
      MessageType.KICK_USER ->
        encodeModel(
          Proto.KickUserPayload.parseFrom(bytes).let { KickUserPayload(it.userId, it.reason.nullIfEmpty()) },
          KickUserPayload.serializer(),
        )
      MessageType.TRANSFER_HOST ->
        encodeModel(
          Proto.TransferHostPayload.parseFrom(bytes).let { TransferHostPayload(it.newHostId) },
          TransferHostPayload.serializer(),
        )
      MessageType.SUGGEST_TRACK ->
        encodeModel(
          Proto.SuggestTrackPayload.parseFrom(bytes).let {
            SuggestTrackPayload(if (it.hasTrackInfo()) it.trackInfo.toModel() else TrackInfo())
          },
          SuggestTrackPayload.serializer(),
        )
      MessageType.APPROVE_SUGGESTION ->
        encodeModel(
          Proto.ApproveSuggestionPayload.parseFrom(bytes).let { ApproveSuggestionPayload(it.suggestionId) },
          ApproveSuggestionPayload.serializer(),
        )
      MessageType.REJECT_SUGGESTION ->
        encodeModel(
          Proto.RejectSuggestionPayload.parseFrom(bytes).let {
            RejectSuggestionPayload(it.suggestionId, it.reason.nullIfEmpty())
          },
          RejectSuggestionPayload.serializer(),
        )
      MessageType.UPDATE_ROOM_SETTINGS ->
        encodeModel(
          Proto.UpdateRoomSettingsPayload.parseFrom(bytes).let {
            UpdateRoomSettingsPayload(it.allowParticipantControl)
          },
          UpdateRoomSettingsPayload.serializer(),
        )
      MessageType.ROOM_CREATED ->
        encodeModel(
          Proto.RoomCreatedPayload.parseFrom(bytes).let {
            RoomCreatedPayload(it.roomCode, it.userId, it.sessionToken)
          },
          RoomCreatedPayload.serializer(),
        )
      MessageType.JOIN_REQUEST ->
        encodeModel(
          Proto.JoinRequestPayload.parseFrom(bytes).let { JoinRequestPayload(it.userId, it.username) },
          JoinRequestPayload.serializer(),
        )
      MessageType.JOIN_APPROVED ->
        encodeModel(
          Proto.JoinApprovedPayload.parseFrom(bytes).let {
            JoinApprovedPayload(
              it.roomCode,
              it.userId,
              it.sessionToken,
              if (it.hasState()) it.state.toModel() else null,
            )
          },
          JoinApprovedPayload.serializer(),
        )
      MessageType.JOIN_REJECTED ->
        encodeModel(
          Proto.JoinRejectedPayload.parseFrom(bytes).let { JoinRejectedPayload(it.reason.nullIfEmpty()) },
          JoinRejectedPayload.serializer(),
        )
      MessageType.USER_JOINED ->
        encodeModel(
          Proto.UserJoinedPayload.parseFrom(bytes).let { UserJoinedPayload(it.userId, it.username) },
          UserJoinedPayload.serializer(),
        )
      MessageType.USER_LEFT ->
        encodeModel(
          Proto.UserLeftPayload.parseFrom(bytes).let { UserLeftPayload(it.userId, it.username) },
          UserLeftPayload.serializer(),
        )
      MessageType.BUFFER_WAIT ->
        encodeModel(
          Proto.BufferWaitPayload.parseFrom(bytes).let {
            BufferWaitPayload(it.trackId, it.waitingForList)
          },
          BufferWaitPayload.serializer(),
        )
      MessageType.BUFFER_COMPLETE ->
        encodeModel(
          Proto.BufferCompletePayload.parseFrom(bytes).let { BufferCompletePayload(it.trackId) },
          BufferCompletePayload.serializer(),
        )
      MessageType.ERROR ->
        encodeModel(
          Proto.ErrorPayload.parseFrom(bytes).let { ErrorPayload(it.code, it.message) },
          ErrorPayload.serializer(),
        )
      MessageType.HOST_CHANGED ->
        encodeModel(
          Proto.HostChangedPayload.parseFrom(bytes).let {
            HostChangedPayload(it.newHostId, it.newHostName)
          },
          HostChangedPayload.serializer(),
        )
      MessageType.KICKED ->
        encodeModel(
          Proto.KickedPayload.parseFrom(bytes).let { KickedPayload(it.reason.nullIfEmpty()) },
          KickedPayload.serializer(),
        )
      MessageType.SYNC_STATE ->
        encodeModel(
          Proto.SyncStatePayload.parseFrom(bytes).let {
            SyncStatePayload(
              currentTrack = if (it.hasCurrentTrack()) it.currentTrack.toModel() else null,
              isPlaying = it.isPlaying,
              position = it.position.nullIfNonPositive(),
              lastUpdate = it.lastUpdate,
              queue = it.queueList.map { track -> track.toModel() }.takeIf { list -> list.isNotEmpty() },
              volume = it.volume.nullIfNonPositive(),
            )
          },
          SyncStatePayload.serializer(),
        )
      MessageType.RECONNECT ->
        encodeModel(
          Proto.ReconnectPayload.parseFrom(bytes).let { ReconnectPayload(it.sessionToken) },
          ReconnectPayload.serializer(),
        )
      MessageType.RECONNECTED ->
        encodeModel(
          Proto.ReconnectedPayload.parseFrom(bytes).let {
            ReconnectedPayload(
              it.roomCode,
              it.userId,
              if (it.hasState()) it.state.toModel() else null,
              it.isHost,
            )
          },
          ReconnectedPayload.serializer(),
        )
      MessageType.USER_RECONNECTED ->
        encodeModel(
          Proto.UserReconnectedPayload.parseFrom(bytes).let {
            UserReconnectedPayload(it.userId, it.username)
          },
          UserReconnectedPayload.serializer(),
        )
      MessageType.USER_DISCONNECTED ->
        encodeModel(
          Proto.UserDisconnectedPayload.parseFrom(bytes).let {
            UserDisconnectedPayload(it.userId, it.username)
          },
          UserDisconnectedPayload.serializer(),
        )
      MessageType.SUGGESTION_RECEIVED ->
        encodeModel(
          Proto.SuggestionReceivedPayload.parseFrom(bytes).let {
            SuggestionReceivedPayload(
              it.suggestionId,
              it.fromUserId,
              it.fromUsername,
              it.trackInfo.toModel(),
            )
          },
          SuggestionReceivedPayload.serializer(),
        )
      MessageType.SUGGESTION_APPROVED ->
        encodeModel(
          Proto.SuggestionApprovedPayload.parseFrom(bytes).let {
            SuggestionApprovedPayload(it.suggestionId, it.trackInfo.toModel())
          },
          SuggestionApprovedPayload.serializer(),
        )
      MessageType.SUGGESTION_REJECTED ->
        encodeModel(
          Proto.SuggestionRejectedPayload.parseFrom(bytes).let {
            SuggestionRejectedPayload(it.suggestionId, it.reason.nullIfEmpty())
          },
          SuggestionRejectedPayload.serializer(),
        )
      MessageType.CHAT -> null
      else -> null
    }

  private fun <T> encodeModel(value: T, serializer: KSerializer<T>): String =
    json.encodeToString(serializer, value)

  private fun TrackInfo.toProto(): Proto.TrackInfo =
    Proto.TrackInfo.newBuilder()
      .setId(id)
      .setTitle(title)
      .setArtist(artist)
      .setAlbum(album.orEmptyString())
      .setDuration(duration)
      .setThumbnail(thumbnail.orEmptyString())
      .setSuggestedBy(suggestedBy.orEmptyString())
      .build()

  private fun Proto.TrackInfo.toModel(): TrackInfo =
    TrackInfo(
      id = id,
      title = title,
      artist = artist,
      album = album.nullIfEmpty(),
      duration = duration,
      thumbnail = thumbnail.nullIfEmpty(),
      suggestedBy = suggestedBy.nullIfEmpty(),
    )

  private fun UserInfo.toProto(): Proto.UserInfo =
    Proto.UserInfo.newBuilder()
      .setUserId(userId)
      .setUsername(username)
      .setIsHost(isHost)
      .setIsConnected(isConnected)
      .build()

  private fun Proto.UserInfo.toModel(): UserInfo =
    UserInfo(userId = userId, username = username, isHost = isHost, isConnected = isConnected)

  private fun RoomState.toProto(): Proto.RoomState =
    Proto.RoomState.newBuilder()
      .setRoomCode(roomCode)
      .setHostId(hostId)
      .addAllUsers(users.map { it.toProto() })
      .apply { this@toProto.currentTrack?.let { setCurrentTrack(it.toProto()) } }
      .setIsPlaying(isPlaying)
      .setPosition(position.orZero())
      .setLastUpdate(lastUpdate)
      .setVolume(volume.orZero())
      .addAllQueue(queue.map { it.toProto() })
      .setAllowParticipantControl(allowParticipantControl)
      .build()

  private fun Proto.RoomState.toModel(): RoomState =
    RoomState(
      roomCode = roomCode,
      hostId = hostId,
      users = usersList.map { it.toModel() },
      currentTrack = if (hasCurrentTrack()) currentTrack.toModel() else null,
      isPlaying = isPlaying,
      position = position.nullIfNonPositive(),
      lastUpdate = lastUpdate,
      volume = volume.nullIfNonPositive(),
      queue = queueList.map { it.toModel() },
      allowParticipantControl = allowParticipantControl,
    )

  private fun String?.orEmptyString(): String = this ?: ""

  private fun String?.nullIfEmpty(): String? = this?.takeIf { it.isNotEmpty() }

  private fun Long?.orZero(): Long = this ?: 0L

  private fun Long.nullIfNonPositive(): Long? = takeIf { it > 0 }

  private fun Float?.orZero(): Float = this ?: 0f

  private fun Float.nullIfNonPositive(): Float? = takeIf { it > 0f }
}
