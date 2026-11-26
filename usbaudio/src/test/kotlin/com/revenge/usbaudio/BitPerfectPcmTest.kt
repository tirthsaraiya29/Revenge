package com.revenge.usbaudio

import androidx.media3.common.C
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BitPerfectPcmTest {

    private fun littleEndian(vararg bytes: Int): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.LITTLE_ENDIAN)
        bytes.forEach { buffer.put(it.toByte()) }
        buffer.flip()
        return buffer
    }

    private fun shortBytes(vararg samples: Short): ByteArray {
        val buffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach(buffer::putShort)
        return buffer.array()
    }

    @Test
    fun sourceBitDepthMapping() {
        assertEquals(16, BitPerfectPcm.sourceBits(C.ENCODING_PCM_16BIT))
        assertEquals(16, BitPerfectPcm.sourceBits(C.ENCODING_PCM_16BIT_BIG_ENDIAN))
        assertEquals(24, BitPerfectPcm.sourceBits(C.ENCODING_PCM_24BIT))
        assertEquals(24, BitPerfectPcm.sourceBits(C.ENCODING_PCM_FLOAT))
        assertEquals(32, BitPerfectPcm.sourceBits(C.ENCODING_PCM_32BIT))
        assertNull(BitPerfectPcm.sourceBits(C.ENCODING_PCM_8BIT))
    }

    @Test
    fun bytesPerSampleMapping() {
        assertEquals(2, BitPerfectPcm.bytesPerSample(C.ENCODING_PCM_16BIT))
        assertEquals(3, BitPerfectPcm.bytesPerSample(C.ENCODING_PCM_24BIT))
        assertEquals(4, BitPerfectPcm.bytesPerSample(C.ENCODING_PCM_FLOAT))
        assertEquals(4, BitPerfectPcm.bytesPerSample(C.ENCODING_PCM_32BIT))
        assertEquals(0, BitPerfectPcm.bytesPerSample(C.ENCODING_PCM_8BIT))
    }

    @Test
    fun outputDepthSelection() {
        assertEquals(16, BitPerfectPcm.outputBits(16, listOf(16, 24, 32)))
        assertEquals(24, BitPerfectPcm.outputBits(24, listOf(16, 24, 32)))
        assertEquals(32, BitPerfectPcm.outputBits(24, listOf(16, 32)))
        assertNull(BitPerfectPcm.outputBits(32, listOf(16, 24)))
    }

    @Test
    fun sixteenBitStereoRoundTripsExactly() {
        val input = ByteBuffer.wrap(shortBytes(12345, -12345)).order(ByteOrder.LITTLE_ENDIAN)
        val output = ByteBuffer.allocateDirect(4).order(ByteOrder.LITTLE_ENDIAN)

        assertTrue(BitPerfectPcm.convert(input, C.ENCODING_PCM_16BIT, 2, 16, output))

        output.flip()
        assertEquals(12345, output.getShort().toInt())
        assertEquals(-12345, output.getShort().toInt())
    }

    @Test
    fun sixteenToTwentyFourLeftAlignsSample() {
        val input = ByteBuffer.wrap(shortBytes(0x1234)).order(ByteOrder.LITTLE_ENDIAN)
        val output = ByteBuffer.allocateDirect(3).order(ByteOrder.LITTLE_ENDIAN)

        assertTrue(BitPerfectPcm.convert(input, C.ENCODING_PCM_16BIT, 1, 24, output))

        output.flip()
        assertEquals(0x00, output.get().toInt() and 0xFF)
        assertEquals(0x34, output.get().toInt() and 0xFF)
        assertEquals(0x12, output.get().toInt() and 0xFF)
    }

    @Test
    fun floatHalfMapsToTwentyFourBitContainer() {
        val input = ByteBuffer.allocateDirect(4).order(ByteOrder.LITTLE_ENDIAN)
        input.putFloat(0.5f)
        input.flip()
        val output = ByteBuffer.allocateDirect(3).order(ByteOrder.LITTLE_ENDIAN)

        assertTrue(BitPerfectPcm.convert(input, C.ENCODING_PCM_FLOAT, 1, 24, output))

        output.flip()
        val first = output.get().toInt() and 0xFF
        val second = output.get().toInt() and 0xFF
        val third = output.get().toInt() and 0xFF
        val raw = first or (second shl 8) or (third shl 16)
        val signed = if (raw and 0x800000 != 0) raw or -0x1000000 else raw
        assertEquals(0.5, signed / 8388608.0, 0.001)
    }

    @Test
    fun eightBitSourceThrowsWithoutAdvancingInput() {
        val input = littleEndian(0x10, 0x20)
        val output = ByteBuffer.allocateDirect(4)

        assertThrows(IllegalArgumentException::class.java) {
            BitPerfectPcm.convert(input, C.ENCODING_PCM_8BIT, 1, 16, output)
        }
        assertEquals(0, input.position())
    }

    @Test
    fun narrowingThirtyTwoToSixteenThrows() {
        val input = ByteBuffer.allocateDirect(4).order(ByteOrder.LITTLE_ENDIAN)
        input.putInt(1000)
        input.flip()
        val output = ByteBuffer.allocateDirect(2)

        assertThrows(IllegalArgumentException::class.java) {
            BitPerfectPcm.convert(input, C.ENCODING_PCM_32BIT, 1, 16, output)
        }
    }
}
