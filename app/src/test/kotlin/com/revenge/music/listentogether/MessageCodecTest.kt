package com.revenge.music.listentogether

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageCodecTest {
    private val codec = MessageCodec()

    @Test
    fun jsonRoundTripPreservesTypeAndPayload() {
        val payload = RoomCreatedPayload("ABC123", "user-1", "token-1")
        val frame = codec.encode(MessageTypes.ROOM_CREATED, payload)
        assertNotNull(frame)
        val decoded = codec.decode(frame!!)!!
        assertEquals(MessageTypes.ROOM_CREATED, decoded.type)
        assertEquals(MessageFormat.JSON, decoded.format)
        val restored = codec.decodePayload(decoded.type, decoded.payload, decoded.format)
        assertEquals(payload, restored)
    }

    @Test
    fun jsonRoundTripKeepsNullToEmptyAsymmetry() {
        val payload = TrackInfo(
            id = "id",
            title = "Title",
            artist = "Artist",
            album = null,
            duration = 1000,
            thumbnail = null,
            suggestedBy = null,
        )
        val frame = codec.encode(MessageTypes.SUGGEST_TRACK, SuggestTrackPayload(payload))!!
        val decoded = codec.decode(frame)!!
        val restored = codec.decodePayload(decoded.type, decoded.payload, decoded.format)
            as SuggestTrackPayload
        assertEquals(payload, restored.trackInfo)
        assertNull(restored.trackInfo.album)
    }

    @Test
    fun formatDetectionFollowsFirstByte() {
        assertEquals(MessageFormat.JSON, codec.detectFormat(ByteArray(0)))
        assertEquals(MessageFormat.JSON, codec.detectFormat("{}".toByteArray()))
        assertEquals(MessageFormat.PROTOBUF, codec.detectFormat(byteArrayOf(0x08, 0x01)))
    }

    @Test
    fun protobufRoundTripAfterUpgrade() {
        codec.upgradeToProtobuf()
        val payload = SyncStatePayload(
            currentTrack = TrackInfo("t1", "Song", "Artist", "Album", 120_000, null, null),
            isPlaying = true,
            position = 5_000,
            lastUpdate = 1_000,
            queue = listOf(TrackInfo("t1", "Song", "Artist")),
            volume = 0.5f,
        )
        val frame = codec.encode(MessageTypes.SYNC_STATE, payload)!!
        val decoded = codec.decode(frame)!!
        assertEquals(MessageFormat.PROTOBUF, decoded.format)
        val restored = codec.decodePayload(decoded.type, decoded.payload, decoded.format)
            as SyncStatePayload
        assertEquals("t1", restored.currentTrack?.id)
        assertEquals(true, restored.isPlaying)
        assertEquals(5_000L, restored.position)
        assertEquals(0.5f, restored.volume!!, 0.0001f)
    }

    @Test
    fun chatStillEncodesAfterProtobufUpgrade() {
        codec.upgradeToProtobuf()
        val frame = codec.encode(MessageTypes.CHAT, ChatPayload("hello"))
        assertNotNull(frame)
        val decoded = codec.decode(frame!!)!!
        assertEquals(MessageFormat.JSON, decoded.format)
        val restored = codec.decodePayload(decoded.type, decoded.payload, decoded.format)
            as ChatPayload
        assertEquals("hello", restored.message)
    }

    @Test
    fun largePayloadIsCompressedAndRestored() {
        codec.upgradeToProtobuf()
        val message = "x".repeat(400)
        val frame = codec.encode(MessageTypes.CHAT, ChatPayload(message))!!
        val decoded = codec.decode(frame)!!
        assertTrue(decoded.compressed)
        val restored = codec.decodePayload(decoded.type, decoded.payload, decoded.format)
            as ChatPayload
        assertEquals(message, restored.message)
    }

    @Test
    fun unknownPayloadTypeReturnsNullWithoutThrowing() {
        assertNull(codec.decodePayload("not_a_type", "{}".toByteArray(), MessageFormat.JSON))
    }

    @Test
    fun malformedFrameDecodesToNull() {
        assertNull(codec.decode(byteArrayOf(0x08)))
        assertNull(codec.decode("{not-json}".toByteArray()))
    }

    @Test
    fun protobufUpgradeNegotiatesOutgoingFormat() {
        assertEquals(MessageFormat.JSON, codec.outgoingFormat)
        codec.upgradeToProtobuf()
        assertEquals(MessageFormat.PROTOBUF, codec.outgoingFormat)
        assertTrue(codec.compressionEnabled)
    }
}
