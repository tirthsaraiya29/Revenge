package com.revenge.usbaudio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.round

/**
 * Converts Media3 PCM buffers between representations without losing the meaningful bits.
 *
 * Media3 decodes 24-bit and float sources into 32-bit containers; this helper left-aligns samples
 * so a 16-bit source can be carried in a 24/32-bit USB subslot, and reports whether float input
 * could be represented exactly at integer 24-bit precision.
 */
@OptIn(UnstableApi::class)
object BitPerfectPcm {

  /** Integer widths that may be used on the wire. */
  val INTEGER_BITS: List<Int> = listOf(16, 24, 32)

  /** Standard audio rates, used when a UAC 1 device advertises a continuous range. */
  val STANDARD_RATES: List<Int> =
    listOf(44100, 48000, 88200, 96000, 176400, 192000, 352800, 384000, 705600, 768000)

  private const val FLOAT_SCALE = 1.0 / 0x7FFFFFFF
  private const val INT24_MAX = 8_388_607.0
  private const val INT24_MIN = -8_388_608.0

  /** Narrowest integer width that is at least [sourceBits] wide, or `null` if none fits. */
  fun outputBits(sourceBits: Int, supported: Collection<Int>): Int? =
    supported.filter { it in INTEGER_BITS && it >= sourceBits }.minOrNull()

  /** Meaningful bit depth of a Media3 PCM encoding, or `null` when it is not integer PCM. */
  fun sourceBits(encoding: Int): Int? =
    when (encoding) {
      C.ENCODING_PCM_16BIT,
      C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 16
      C.ENCODING_PCM_24BIT,
      C.ENCODING_PCM_24BIT_BIG_ENDIAN,
      C.ENCODING_PCM_FLOAT -> 24
      C.ENCODING_PCM_32BIT,
      C.ENCODING_PCM_32BIT_BIG_ENDIAN -> 32
      else -> null
    }

  /** Bytes occupied by one sample of [encoding] in the source buffer. */
  fun bytesPerSample(encoding: Int): Int =
    when (encoding) {
      C.ENCODING_PCM_16BIT,
      C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 2
      C.ENCODING_PCM_24BIT,
      C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 3
      C.ENCODING_PCM_32BIT,
      C.ENCODING_PCM_32BIT_BIG_ENDIAN,
      C.ENCODING_PCM_FLOAT -> 4
      else -> 0
    }

  /**
   * Converts whole frames from [input] into little-endian integer PCM of [outputBits], writing to
   * [output]. Both buffer positions advance by the amount consumed/produced. Returns `false` when
   * float input contained values that are not exactly representable at 24-bit precision.
   *
   * @throws IllegalArgumentException if the encoding is unsupported or the target would narrow.
   */
  fun convert(
    input: ByteBuffer,
    encoding: Int,
    channelCount: Int,
    outputBits: Int,
    output: ByteBuffer,
  ): Boolean {
    val inBytes = bytesPerSample(encoding)
    val source = requireNotNull(sourceBits(encoding)) { "Unsupported PCM encoding $encoding" }
    require(channelCount > 0) { "channelCount must be positive" }
    require(outputBits in INTEGER_BITS && outputBits >= source) {
      "Output bit depth $outputBits would reduce $source-bit precision"
    }
    val outBytes = outputBits / 8
    val inputFrameBytes = inBytes * channelCount
    val outputFrameBytes = outBytes * channelCount
    val frames =
      minOf(input.remaining() / inputFrameBytes, output.remaining() / outputFrameBytes)

    input.order(orderFor(encoding))
    output.order(ByteOrder.LITTLE_ENDIAN)
    var exact = true
    repeat(frames * channelCount) {
      val sample =
        when (inBytes) {
          2 -> input.short.toInt() shl 16
          3 -> read24(input, isBigEndian(encoding)) shl 8
          4 ->
            if (encoding == C.ENCODING_PCM_FLOAT) {
              val value = input.float
              val recovered = recover24(value)
              if (exact && !isExact24(value, recovered)) exact = false
              recovered shl 8
            } else {
              input.int
            }
          else -> throw IllegalArgumentException("Unsupported PCM encoding $encoding")
        }
      writeSample(output, sample, outputBits)
    }
    return exact
  }

  internal fun writeSample(output: ByteBuffer, sample: Int, outputBits: Int) {
    when (outputBits) {
      16 -> output.putShort((sample shr 16).toShort())
      24 -> {
        output.put((sample shr 8).toByte())
        output.put((sample shr 16).toByte())
        output.put((sample shr 24).toByte())
      }
      else -> output.putInt(sample)
    }
  }

  internal fun orderFor(encoding: Int): ByteOrder =
    if (isBigEndian(encoding)) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN

  internal fun recover24(value: Float): Int {
    if (value.isNaN()) return 0
    val scaled = round(value.toDouble() * 0x7FFFFFFF / 256.0)
    return scaled.coerceIn(INT24_MIN, INT24_MAX).toInt()
  }

  internal fun isExact24(value: Float, sample: Int): Boolean =
    value == sample / 8_388_608f || value == (FLOAT_SCALE * (sample shl 8)).toFloat()

  private fun isBigEndian(encoding: Int): Boolean =
    encoding == C.ENCODING_PCM_16BIT_BIG_ENDIAN ||
      encoding == C.ENCODING_PCM_24BIT_BIG_ENDIAN ||
      encoding == C.ENCODING_PCM_32BIT_BIG_ENDIAN

  private fun read24(input: ByteBuffer, bigEndian: Boolean): Int {
    val first = input.get().toInt() and 0xFF
    val second = input.get().toInt() and 0xFF
    val third = input.get().toInt() and 0xFF
    return if (bigEndian) {
      // Sign-extending 24-bit big-endian sample.
      ((first shl 16) or (second shl 8) or third) shl 8 shr 8
    } else {
      (third shl 16) or (second shl 8) or first
    }
  }
}
