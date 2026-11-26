package com.revenge.music.listentogether

import com.revenge.music.listentogether.proto.Listentogether
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageCodecTest {

  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

  private val track =
    TrackInfo(
      id = "vid",
      title = "Title",
      artist = "Artist",
      album = "Album",
      duration = 123_000,
      thumbnail = "https://img",
      suggestedBy = "bob",
    )

  private val room =
    RoomState(
      roomCode = "ABC",
      hostId = "u1",
      users = listOf(UserInfo(userId = "u1", username = "alice", isHost = true, isConnected = true)),
      currentTrack = track,
      isPlaying = true,
      position = 42_000,
      lastUpdate = 1_700_000_000_000,
      volume = 0.5f,
      queue = listOf(track),
      allowParticipantControl = true,
    )

  // ------------------------------------------------------------------ JSON framing

  @Test
  fun jsonRoundTripKeepsTypeAndPayload() {
    val codec = MessageCodec()
    val frame = codec.encode(MessageType.CREATE_ROOM, """{"username":"alice"}""")

    val decoded = codec.decode(frame)!!

    assertEquals(MessageType.CREATE_ROOM, decoded.type)
    assertEquals(
      json.parseToJsonElement("""{"username":"alice"}"""),
      json.parseToJsonElement(decoded.payloadJson!!),
    )
  }

  @Test
  fun nullPayloadRoundTrips() {
    val codec = MessageCodec()
    val decoded = codec.decode(codec.encode(MessageType.PING, null))
    assertEquals(MessageType.PING, decoded?.type)
    assertEquals(null, decoded?.payloadJson)
  }

  @Test
  fun jsonFirstByteIsDetectedAsJson() {
    val codec = MessageCodec()
    assertTrue(codec.isJsonFrame("""{"type":"ping","payload":null}""".toByteArray()))
    assertFalse(codec.shouldUpgradeToProtobuf("""{"type":"ping"}""".toByteArray()))
  }

  // ------------------------------------------------------------------ typed protobuf

  @Test
  fun typedProtobufRoundTripsEveryDefinedPayload() {
    val codec = MessageCodec()
    codec.useProtobuf = true

    roundTrip(codec, MessageType.CREATE_ROOM, CreateRoomPayload("alice"), CreateRoomPayload.serializer())
    roundTrip(codec, MessageType.JOIN_ROOM, JoinRoomPayload("ABC", "bob"), JoinRoomPayload.serializer())
    roundTrip(codec, MessageType.APPROVE_JOIN, ApproveJoinPayload("u2"), ApproveJoinPayload.serializer())
    roundTrip(codec, MessageType.REJECT_JOIN, RejectJoinPayload("u2", "nope"), RejectJoinPayload.serializer())
    roundTrip(
      codec,
      MessageType.PLAYBACK_ACTION,
      PlaybackActionPayload(
        action = PlaybackAction.PLAY,
        trackId = "vid",
        position = 5_000,
        trackInfo = track,
        insertNext = true,
        queue = listOf(track),
        queueTitle = "Mix",
        volume = 0.4f,
        serverTime = 1_700_000_000_000,
      ),
      PlaybackActionPayload.serializer(),
    )
    roundTrip(codec, MessageType.BUFFER_READY, BufferReadyPayload("vid"), BufferReadyPayload.serializer())
    roundTrip(codec, MessageType.KICK_USER, KickUserPayload("u2", "bye"), KickUserPayload.serializer())
    roundTrip(codec, MessageType.TRANSFER_HOST, TransferHostPayload("u3"), TransferHostPayload.serializer())
    roundTrip(codec, MessageType.SUGGEST_TRACK, SuggestTrackPayload(track), SuggestTrackPayload.serializer())
    roundTrip(
      codec,
      MessageType.SUGGESTION_RECEIVED,
      SuggestionReceivedPayload("s1", "u2", "bob", track),
      SuggestionReceivedPayload.serializer(),
    )
    roundTrip(
      codec,
      MessageType.APPROVE_SUGGESTION,
      ApproveSuggestionPayload("s1"),
      ApproveSuggestionPayload.serializer(),
    )
    roundTrip(
      codec,
      MessageType.REJECT_SUGGESTION,
      RejectSuggestionPayload("s1", "no"),
      RejectSuggestionPayload.serializer(),
    )
    roundTrip(
      codec,
      MessageType.SUGGESTION_APPROVED,
      SuggestionApprovedPayload("s1", track),
      SuggestionApprovedPayload.serializer(),
    )
    roundTrip(
      codec,
      MessageType.SUGGESTION_REJECTED,
      SuggestionRejectedPayload("s1", "no"),
      SuggestionRejectedPayload.serializer(),
    )
    roundTrip(
      codec,
      MessageType.UPDATE_ROOM_SETTINGS,
      UpdateRoomSettingsPayload(true),
      UpdateRoomSettingsPayload.serializer(),
    )
    roundTrip(
      codec,
      MessageType.ROOM_CREATED,
      RoomCreatedPayload("ABC", "u1", "token"),
      RoomCreatedPayload.serializer(),
    )
    roundTrip(codec, MessageType.JOIN_REQUEST, JoinRequestPayload("u2", "bob"), JoinRequestPayload.serializer())
    roundTrip(
      codec,
      MessageType.JOIN_APPROVED,
      JoinApprovedPayload("ABC", "u2", "token", room),
      JoinApprovedPayload.serializer(),
    )
    roundTrip(codec, MessageType.JOIN_REJECTED, JoinRejectedPayload("full"), JoinRejectedPayload.serializer())
    roundTrip(codec, MessageType.USER_JOINED, UserJoinedPayload("u2", "bob"), UserJoinedPayload.serializer())
    roundTrip(codec, MessageType.USER_LEFT, UserLeftPayload("u2", "bob"), UserLeftPayload.serializer())
    roundTrip(
      codec,
      MessageType.BUFFER_WAIT,
      BufferWaitPayload("vid", listOf("u2", "u3")),
      BufferWaitPayload.serializer(),
    )
    roundTrip(codec, MessageType.BUFFER_COMPLETE, BufferCompletePayload("vid"), BufferCompletePayload.serializer())
    roundTrip(codec, MessageType.ERROR, ErrorPayload("code", "message"), ErrorPayload.serializer())
    roundTrip(
      codec,
      MessageType.HOST_CHANGED,
      HostChangedPayload("u2", "bob"),
      HostChangedPayload.serializer(),
    )
    roundTrip(codec, MessageType.KICKED, KickedPayload("bye"), KickedPayload.serializer())
    roundTrip(
      codec,
      MessageType.SYNC_STATE,
      SyncStatePayload(track, true, 42_000, 1_700_000_000_000, listOf(track), 0.7f),
      SyncStatePayload.serializer(),
    )
    roundTrip(codec, MessageType.RECONNECT, ReconnectPayload("token"), ReconnectPayload.serializer())
    roundTrip(
      codec,
      MessageType.RECONNECTED,
      ReconnectedPayload("ABC", "u1", room, true),
      ReconnectedPayload.serializer(),
    )
    roundTrip(
      codec,
      MessageType.USER_RECONNECTED,
      UserReconnectedPayload("u2", "bob"),
      UserReconnectedPayload.serializer(),
    )
    roundTrip(
      codec,
      MessageType.USER_DISCONNECTED,
      UserDisconnectedPayload("u2", "bob"),
      UserDisconnectedPayload.serializer(),
    )
  }

  @Test
  fun roomStateUsesDocumentedFieldTags() {
    val codec = MessageCodec()
    codec.useProtobuf = true
    val state = RoomState(roomCode = "R", hostId = "H", volume = 0.5f, allowParticipantControl = true)
    val frame =
      codec.encode(
        MessageType.JOIN_APPROVED,
        json.encodeToString(
          JoinApprovedPayload.serializer(),
          JoinApprovedPayload(roomCode = "R", userId = "H", sessionToken = "t", state = state),
        ),
      )
    val envelope = Listentogether.Envelope.parseFrom(frame)
    val roomStateBytes =
      Listentogether.JoinApprovedPayload.parseFrom(envelope.payload.toByteArray())
        .state
        .toByteArray()
        .toList()
    // Field 8 (float volume) -> (8 << 3) | 5 = 0x45; field 10 (bool) -> (10 << 3) | 0 = 0x50.
    val volumeTag = roomStateBytes.indexOf(0x45)
    val settingsTag = roomStateBytes.indexOf(0x50)
    assertTrue("volume tag missing", volumeTag >= 0)
    assertTrue("settings tag missing", settingsTag >= 0)
    assertTrue("tags out of order", volumeTag < settingsTag)
  }

  @Test
  fun largeTypedPayloadIsCompressedAndRestored() {
    val codec = MessageCodec()
    codec.useProtobuf = true
    codec.compressionEnabled = true
    val payload = json.encodeToString(CreateRoomPayload.serializer(), CreateRoomPayload("x".repeat(400)))

    val frame = codec.encode(MessageType.CREATE_ROOM, payload)
    val envelope = Listentogether.Envelope.parseFrom(frame)

    assertTrue(envelope.compressed)
    assertEquals(MessageType.CREATE_ROOM, envelope.type)
    val decoded = codec.decode(frame)!!
    assertEquals(
      CreateRoomPayload("x".repeat(400)),
      json.decodeFromString(CreateRoomPayload.serializer(), decoded.payloadJson!!),
    )
  }

  @Test
  fun smallProtobufPayloadStaysUncompressed() {
    val codec = MessageCodec()
    codec.useProtobuf = true
    codec.compressionEnabled = true
    val frame = codec.encode(MessageType.PING, null)
    val envelope = Listentogether.Envelope.parseFrom(frame)
    assertFalse(envelope.compressed)
  }

  @Test
  fun binaryFrameUpgradesAJsonClient() {
    val sender = MessageCodec()
    sender.useProtobuf = true
    val frame =
      sender.encode(
        MessageType.JOIN_ROOM,
        json.encodeToString(JoinRoomPayload.serializer(), JoinRoomPayload("ABC", "bob")),
      )

    val receiver = MessageCodec()
    assertTrue(receiver.shouldUpgradeToProtobuf(frame))
    receiver.useProtobuf = true
    receiver.compressionEnabled = true

    val decoded = receiver.decode(frame)!!
    assertEquals(MessageType.JOIN_ROOM, decoded.type)
    assertEquals(
      JoinRoomPayload("ABC", "bob"),
      json.decodeFromString(JoinRoomPayload.serializer(), decoded.payloadJson!!),
    )
  }

  // ------------------------------------------------------------------ fallback and malformed

  @Test
  fun chatFallsBackToJsonAfterProtobufUpgrade() {
    val codec = MessageCodec()
    codec.useProtobuf = true
    codec.compressionEnabled = true
    val payload =
      json.encodeToString(
        ChatPayload.serializer(),
        ChatPayload("hello", RepliedMessage("bob", "yo")),
      )

    val frame = codec.encode(MessageType.CHAT, payload)
    // No proto definition: the frame is JSON (possibly whole-frame gzipped), not an envelope.
    assertTrue(codec.isJsonFrame(frame))
    val decoded = codec.decode(frame)!!
    assertEquals(MessageType.CHAT, decoded.type)
    assertEquals(
      ChatPayload("hello", RepliedMessage("bob", "yo")),
      json.decodeFromString(ChatPayload.serializer(), decoded.payloadJson!!),
    )
  }

  @Test
  fun largeChatFallbackIsWholeFrameCompressedAndRestored() {
    val codec = MessageCodec()
    codec.useProtobuf = true
    codec.compressionEnabled = true
    val chat = ChatPayload("x".repeat(400), RepliedMessage("bob", "y".repeat(100)))
    val payload = json.encodeToString(ChatPayload.serializer(), chat)

    val frame = codec.encode(MessageType.CHAT, payload)
    assertEquals(0x1f, frame[0].toInt() and 0xff)
    assertEquals(0x8b, frame[1].toInt() and 0xff)
    val decoded = codec.decode(frame)!!
    assertEquals(chat, json.decodeFromString(ChatPayload.serializer(), decoded.payloadJson!!))
  }

  @Test
  fun malformedFramesAreDroppedWithoutThrowing() {
    val codec = MessageCodec()
    // Truncated length-delimited field: declares 127 bytes but has none.
    assertNull(codec.decode(byteArrayOf(0x0A, 0x7F)))
    assertNull(codec.decode(byteArrayOf(0x1f, 0x8b.toByte(), 0x00)))
    assertNull(codec.decodePayload("unknown_type", byteArrayOf(0x08, 0x01)))
  }

  @Test
  fun typedEnvelopeDecodesToJsonForKnownType() {
    val proto =
      Listentogether.CreateRoomPayload.newBuilder().setUsername("alice").build().toByteArray()
    val decoded = PayloadMapper.decode(MessageType.CREATE_ROOM, proto)!!
    assertEquals(
      CreateRoomPayload("alice"),
      json.decodeFromString(CreateRoomPayload.serializer(), decoded),
    )
  }

  // ------------------------------------------------------------------ helpers

  private fun <T> roundTrip(
    codec: MessageCodec,
    type: String,
    value: T,
    serializer: KSerializer<T>,
  ) {
    val payload = json.encodeToString(serializer, value)
    val decoded = codec.decode(codec.encode(type, payload))
    assertNotNull("null decode for $type", decoded)
    assertEquals(type, decoded!!.type)
    val restored = json.decodeFromString(serializer, decoded.payloadJson!!)
    assertEquals("round trip mismatch for $type", value, restored)
  }
}
