package com.revenge.usbaudio

import androidx.media3.common.C
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BitPerfectPcmTest {

  @Test
  fun encodingMetadataMatchesMedia3() {
    assertEquals(16, BitPerfectPcm.sourceBits(C.ENCODING_PCM_16BIT))
    assertEquals(24, BitPerfectPcm.sourceBits(C.ENCODING_PCM_24BIT))
    assertEquals(24, BitPerfectPcm.sourceBits(C.ENCODING_PCM_FLOAT))
    assertEquals(32, BitPerfectPcm.sourceBits(C.ENCODING_PCM_32BIT))
    assertNull(BitPerfectPcm.sourceBits(C.ENCODING_PCM_8BIT))

    assertEquals(2, BitPerfectPcm.bytesPerSample(C.ENCODING_PCM_16BIT))
    assertEquals(3, BitPerfectPcm.bytesPerSample(C.ENCODING_PCM_24BIT))
    assertEquals(4, BitPerfectPcm.bytesPerSample(C.ENCODING_PCM_FLOAT))
    assertEquals(4, BitPerfectPcm.bytesPerSample(C.ENCODING_PCM_32BIT))
    assertEquals(0, BitPerfectPcm.bytesPerSample(C.ENCODING_PCM_8BIT))
  }

  @Test
  fun outputDepthPicksNarrowestSufficientContainer() {
    assertEquals(16, BitPerfectPcm.outputBits(16, listOf(16, 24, 32)))
    assertEquals(24, BitPerfectPcm.outputBits(24, listOf(16, 24, 32)))
    assertEquals(32, BitPerfectPcm.outputBits(24, listOf(16, 32)))
    assertNull(BitPerfectPcm.outputBits(32, listOf(16, 24)))
  }

  @Test
  fun sixteenBitRoundTripIsExact() {
    val input = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
    input.putShort(12345)
    input.putShort((-12345).toShort())
    input.flip()

    val output = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
    assertTrue(BitPerfectPcm.convert(input, C.ENCODING_PCM_16BIT, 2, 16, output))

    output.flip()
    assertEquals(12345, output.short.toInt())
    assertEquals(-12345, output.short.toInt())
  }

  @Test
  fun sixteenBitLeftAlignsInto24BitContainer() {
    val input = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
    input.putShort(0x1234)
    input.flip()

    val output = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN)
    assertTrue(BitPerfectPcm.convert(input, C.ENCODING_PCM_16BIT, 1, 24, output))

    output.flip()
    assertEquals(0, output.get().toInt() and 0xFF)
    assertEquals(0x34, output.get().toInt() and 0xFF)
    assertEquals(0x12, output.get().toInt() and 0xFF)
  }

  @Test
  fun floatSourceIsConvertedTo24Bit() {
    val input = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
    input.putFloat(0.5f)
    input.flip()

    val output = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN)
    BitPerfectPcm.convert(input, C.ENCODING_PCM_FLOAT, 1, 24, output)

    output.flip()
    val rawSigned =
      (output.get().toInt() and 0xFF) or
        ((output.get().toInt() and 0xFF) shl 8) or
        (output.get().toInt() shl 16)
    val value = rawSigned / 8_388_608.0f
    assertTrue(kotlin.math.abs(value - 0.5f) < 0.001f)
  }

  @Test
  fun unsigned8BitIsRejected() {
    val input = ByteBuffer.allocate(1)
    val output = ByteBuffer.allocate(2)
    var threw = false
    try {
      BitPerfectPcm.convert(input, C.ENCODING_PCM_8BIT, 1, 16, output)
    } catch (_: IllegalArgumentException) {
      threw = true
    }
    assertTrue(threw)
    assertTrue(input.hasRemaining())
  }

  @Test
  fun narrowingConversionIsRejected() {
    val input = ByteBuffer.allocate(4)
    val output = ByteBuffer.allocate(2)
    var threw = false
    try {
      BitPerfectPcm.convert(input, C.ENCODING_PCM_32BIT, 1, 16, output)
    } catch (_: IllegalArgumentException) {
      threw = true
    }
    assertTrue(threw)
  }
}
