package com.revenge.music.listentogether

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageCodecTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private inline fun <reified T> encodePayload(payload: T): String =
        json.encodeToString(payload)

    @Test
    fun jsonRoundTripPreservesTypeAndPayload() {
        val codec = MessageCodec()
        val payload = encodePayload(RoomCreatedPayload("ABC123", "user-1", "token-1"))
        val frame = codec.encode(MessageType.ROOM_CREATED, payload)
        val decoded = codec.decode(frame)!!
        assertEquals(MessageType.ROOM_CREATED, decoded.type)
        val restored = json.decodeFromString(
            RoomCreatedPayload.serializer(),
            decoded.payloadJson!!,
        )
        assertEquals(RoomCreatedPayload("ABC123", "user-1", "token-1"), restored)
    }

    @Test
    fun nullPayloadEncodesAsNull() {
        val codec = MessageCodec()
        val decoded = codec.decode(codec.encode(MessageType.PING, null))!!
        assertEquals(MessageType.PING, decoded.type)
        assertNull(decoded.payloadJson)
    }

    @Test
    fun formatDetectionFollowsFirstByte() {
        val codec = MessageCodec()
        assertTrue(codec.isJsonFrame(ByteArray(0)))
        assertTrue(codec.isJsonFrame("{}".toByteArray()))
        assertFalse(codec.shouldUpgradeToProtobuf("{}".toByteArray()))
        assertFalse(codec.isJsonFrame(byteArrayOf(0x08, 0x01)))
        assertTrue(codec.shouldUpgradeToProtobuf(byteArrayOf(0x08, 0x01)))
    }

    @Test
    fun protobufRoundTripAfterUpgrade() {
        val codec = MessageCodec()
        codec.useProtobuf = true
        codec.compressionEnabled = true
        val payload = encodePayload(
            PlaybackActionPayload(
                action = PlaybackAction.PLAY,
                trackId = "t1",
                position = 5000L,
                volume = 0.5f,
            ),
        )
        val frame = codec.encode(MessageType.PLAYBACK_ACTION, payload)
        val decoded = codec.decode(frame)!!
        assertEquals(MessageType.PLAYBACK_ACTION, decoded.type)
        val restored = json.decodeFromString(
            PlaybackActionPayload.serializer(),
            decoded.payloadJson!!,
        )
        assertEquals("t1", restored.trackId)
        assertEquals(5000L, restored.position)
        assertEquals(0.5f, restored.volume!!, 0.0001f)
    }

    @Test
    fun chatStillEncodesAfterProtobufUpgrade() {
        val codec = MessageCodec()
        codec.useProtobuf = true
        codec.compressionEnabled = true
        val payload = encodePayload(ChatPayload("hello"))
        val frame = codec.encode(MessageType.CHAT, payload)
        val decoded = codec.decode(frame)!!
        assertEquals(MessageType.CHAT, decoded.type)
        val restored = json.decodeFromString(ChatPayload.serializer(), decoded.payloadJson!!)
        assertEquals("hello", restored.message)
    }

    @Test
    fun largePayloadIsCompressedAndRestored() {
        val codec = MessageCodec()
        codec.compressionEnabled = true
        val message = "x".repeat(500)
        val payload = encodePayload(ChatPayload(message))
        val frame = codec.encode(MessageType.CHAT, payload)
        assertTrue(codec.isJsonFrame(frame))
        val decoded = codec.decode(frame)!!
        val restored = json.decodeFromString(ChatPayload.serializer(), decoded.payloadJson!!)
        assertEquals(message, restored.message)
    }

    @Test
    fun malformedFrameDecodesToNull() {
        val codec = MessageCodec()
        assertNull(codec.decode(byteArrayOf(0x08)))
        assertNull(codec.decode("{not-json}".toByteArray()))
    }

    @Test
    fun unknownTypeIsStillDecodable() {
        val codec = MessageCodec()
        val decoded = codec.decode(codec.encode("nonexistent_type", "{}"))!!
        assertEquals("nonexistent_type", decoded.type)
        assertNotNull(decoded.payloadJson)
    }

    @Test
    fun upgradeFlagsAreControllable() {
        val codec = MessageCodec()
        assertFalse(codec.useProtobuf)
        codec.useProtobuf = true
        codec.compressionEnabled = true
        assertTrue(codec.useProtobuf)
        assertTrue(codec.compressionEnabled)
    }
}
