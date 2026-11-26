package com.revenge.music.listentogether

import com.revenge.music.listentogether.proto.Listentogether
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageCodecTest {

  private val json = Json { ignoreUnknownKeys = true }

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
  fun largeProtobufPayloadIsCompressed() {
    val codec = MessageCodec()
    codec.useProtobuf = true
    codec.compressionEnabled = true
    val payload = """{"text":"${"x".repeat(400)}"}"""

    val frame = codec.encode(MessageType.CHAT, payload)
    val envelope = Listentogether.Envelope.parseFrom(frame)

    assertTrue(envelope.compressed)
    assertEquals(MessageType.CHAT, envelope.type)

    val decoded = codec.decode(frame)!!
    assertEquals(
      json.parseToJsonElement(payload),
      json.parseToJsonElement(decoded.payloadJson!!),
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
    val frame = sender.encode(MessageType.JOIN_ROOM, """{"room_code":"ABC","username":"bob"}""")

    val receiver = MessageCodec()
    assertTrue(receiver.shouldUpgradeToProtobuf(frame))
    receiver.useProtobuf = true
    receiver.compressionEnabled = true

    val decoded = receiver.decode(frame)!!
    assertEquals(MessageType.JOIN_ROOM, decoded.type)
    assertTrue(decoded.payloadJson!!.contains("ABC"))
  }

  @Test
  fun jsonFirstByteIsDetectedAsJson() {
    val codec = MessageCodec()
    assertTrue(codec.isJsonFrame("""{"type":"ping","payload":null}""".toByteArray()))
    assertFalse(codec.shouldUpgradeToProtobuf("""{"type":"ping"}""".toByteArray()))
  }
}
