package com.revenge.usbaudio

import androidx.media3.common.C
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * PCM container conversion helper. This class only changes the integer container width of PCM
 * samples; it makes no claim about the transport being bit-perfect or exclusive.
 */
object BitPerfectPcm {

    val INTEGER_BITS: List<Int> = listOf(16, 24, 32)

    val STANDARD_RATES: List<Int> = listOf(
        44100, 48000, 88200, 96000, 176400, 192000, 352800, 384000, 705600, 768000,
    )

    fun outputBits(sourceBits: Int, supported: Collection<Int>): Int? =
        supported.filter { it in INTEGER_BITS && it >= sourceBits }.minOrNull()

    fun sourceBits(encoding: Int): Int? = when (encoding) {
        C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 16
        C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN, C.ENCODING_PCM_FLOAT -> 24
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN -> 32
        else -> null
    }

    fun bytesPerSample(encoding: Int): Int = when (encoding) {
        C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 2
        C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 3
        C.ENCODING_PCM_FLOAT, C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN -> 4
        else -> 0
    }

    fun convert(
        input: ByteBuffer,
        encoding: Int,
        channelCount: Int,
        outputBits: Int,
        output: ByteBuffer,
    ): Boolean {
        val precision = sourceBits(encoding)
            ?: throw IllegalArgumentException("Unsupported source encoding: $encoding")
        val sourceBytes = bytesPerSample(encoding)
        if (outputBits !in INTEGER_BITS) {
            throw IllegalArgumentException("Unsupported output bit depth: $outputBits")
        }
        if (outputBits < precision) {
            throw IllegalArgumentException("Output depth $outputBits narrows source precision $precision")
        }
        if (channelCount <= 0) {
            throw IllegalArgumentException("Invalid channel count: $channelCount")
        }

        val bigEndian = encoding == C.ENCODING_PCM_16BIT_BIG_ENDIAN ||
            encoding == C.ENCODING_PCM_24BIT_BIG_ENDIAN ||
            encoding == C.ENCODING_PCM_32BIT_BIG_ENDIAN
        val destinationBytes = outputBits / 8
        val frameBytesIn = sourceBytes * channelCount
        val frameBytesOut = destinationBytes * channelCount
        val source = input.duplicate().order(if (bigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN)

        val framesAvailable = source.remaining() / frameBytesIn
        val framesByOutput = output.remaining() / frameBytesOut
        val frames = minOf(framesAvailable, framesByOutput)
        val isFloat = encoding == C.ENCODING_PCM_FLOAT
        var exact = true

        for (frame in 0 until frames) {
            for (channel in 0 until channelCount) {
                val value = if (isFloat) {
                    val sample = source.getFloat().toDouble()
                    val scaled = sample * FLOAT_SCALE
                    if (!scaled.isFinite()) {
                        exact = false
                        0
                    } else {
                        val truncated = scaled.toInt()
                        if (truncated.toDouble() != scaled) exact = false
                        val bounded = truncated.coerceIn(MIN_24, MAX_24)
                        if (bounded != truncated) exact = false
                        if (outputBits > 24) bounded shl (outputBits - 24) else bounded
                    }
                } else {
                    val raw = when (sourceBytes) {
                        2 -> source.getShort().toInt()
                        3 -> readInt24(source, bigEndian)
                        else -> source.getInt()

                    }
                    if (outputBits > precision) raw shl (outputBits - precision) else raw
                }
                putLittleEndian(output, value, destinationBytes)
            }
        }

        input.position(input.position() + frames * frameBytesIn)
        return exact
    }

    private fun readInt24(buffer: ByteBuffer, bigEndian: Boolean): Int {
        val first = buffer.get().toInt() and 0xFF
        val second = buffer.get().toInt() and 0xFF
        val third = buffer.get().toInt() and 0xFF
        val raw = if (bigEndian) {
            (first shl 16) or (second shl 8) or third
        } else {
            first or (second shl 8) or (third shl 16)
        }
        return if (raw and SIGN_BIT_24 != 0) raw or -FULL_RANGE_24 else raw
    }

    private fun putLittleEndian(buffer: ByteBuffer, value: Int, bytes: Int) {
        buffer.put((value and 0xFF).toByte())
        if (bytes >= 2) buffer.put(((value ushr 8) and 0xFF).toByte())
        if (bytes >= 3) buffer.put(((value ushr 16) and 0xFF).toByte())
        if (bytes >= 4) buffer.put(((value ushr 24) and 0xFF).toByte())
    }

    private const val FLOAT_SCALE = 8388608.0
    private const val MIN_24 = -8388608
    private const val MAX_24 = 8388607
    private const val SIGN_BIT_24 = 0x800000
    private const val FULL_RANGE_24 = 0x1000000
}
