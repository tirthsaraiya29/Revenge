package com.revenge.usbaudio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.revenge.usbaudio.model.DacCapabilities

/**
 * Software PCM transport for a USB audio device. Playback is rendered through a platform
 * [AudioTrack] and routed by the platform mixer; a bounded byte FIFO is used when the platform
 * track cannot be created. No native isochronous backend is present.
 */
class UsbAudioDriver {

    private val ring = ByteArray(FIFO_CAPACITY)
    private val ringLock = Any()
    private var ringHead = 0
    private var ringSize = 0

    @Volatile
    private var fifoFramesPlayed = 0L

    @Volatile
    private var track: AudioTrack? = null

    @Volatile
    private var softwareMultiplier = 1.0

    @Volatile
    private var sampleBytes = 2

    @Volatile
    private var channels = 2

    @Volatile
    private var frameSize = 4

    @Volatile
    private var platformFramesWritten = 0L

    companion object {
        val nativeLoaded: Boolean = false
        private const val FIFO_CAPACITY = 131_072
        private const val VERSION = "1.0.0-usbaudio"
    }

    fun isLoaded(): Boolean = true

    fun getNativeDriverVersion(): String = VERSION

    fun testRingBufferWrite(data: ByteArray): Int = synchronized(ringLock) {
        appendLocked(data, data.size)
    }

    fun testRingBufferRead(size: Int): ByteArray = synchronized(ringLock) {
        val count = size.coerceIn(0, ringSize)
        val result = ByteArray(count)
        for (index in 0 until count) {
            result[index] = ring[(ringHead + index) % FIFO_CAPACITY]
        }
        ringHead = (ringHead + count) % FIFO_CAPACITY
        ringSize -= count
        result
    }

    fun testRingBufferFlush() {
        synchronized(ringLock) {
            ringHead = 0
            ringSize = 0
        }
    }

    fun parseDescriptors(rawDescriptors: ByteArray): DacCapabilities =
        UsbDescriptorParser.parse(rawDescriptors)

    fun calculateNominalPacketSamples(sampleRate: Int): Int =
        if (sampleRate <= 0) 0 else sampleRate / 8000

    @Synchronized
    fun startStream(
        fd: Int,
        interfaceNumber: Int = 0,
        altSetting: Int = 1,
        dataEp: Int,
        syncEp: Int = -1,
        sampleRate: Int,
        bitDepth: Int = 16,
        channels: Int = 2,
    ): Int {
        releaseTrack()
        discardFifo()
        sampleBytes = (bitDepth + 7) / 8
        this.channels = if (channels > 0) channels else 1
        frameSize = (sampleBytes * this.channels).coerceAtLeast(1)
        platformFramesWritten = 0L
        fifoFramesPlayed = 0L

        val eligible = bitDepth == 16 && this.channels <= 2 && sampleRate in 8_000..192_000
        if (eligible) {
            track = openTrack(sampleRate, this.channels)
        }
        return 0
    }

    @Synchronized
    fun stopStream(): Int {
        releaseTrack()
        discardFifo()
        platformFramesWritten = 0L
        fifoFramesPlayed = 0L
        return 0
    }

    fun writeAudio(buffer: ByteArray, size: Int = buffer.size): Int {
        val requested = minOf(size, buffer.size)
        if (requested <= 0) return 0

        val payload = buffer.copyOf(requested)
        val multiplier = softwareMultiplier
        if (multiplier != 1.0) {
            attenuate(payload, sampleBytes, multiplier)
        }

        val active = track
        if (active != null) {
            return try {
                val written = active.write(payload, 0, requested, AudioTrack.WRITE_NON_BLOCKING)
                if (written < 0) {
                    0
                } else {
                    platformFramesWritten += written / frameSize
                    written
                }
            } catch (error: Exception) {
                0
            }
        }

        val copied = synchronized(ringLock) { appendLocked(payload, requested) }
        fifoFramesPlayed += copied / frameSize
        return copied
    }

    fun getAvailableWrite(): Int {
        val active = track
        if (active == null) {
            return synchronized(ringLock) { (FIFO_CAPACITY - ringSize).coerceIn(0, Int.MAX_VALUE) }
        }
        return try {
            val pendingBytes = pendingPlatformFrames(active) * frameSize
            (active.bufferSizeInFrames.toLong() * frameSize - pendingBytes).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        } catch (error: Exception) {
            0
        }
    }

    fun setSoftwareVolumeMultiplier(multiplier: Double) {
        val bounded = multiplier.coerceIn(0.0, 1.0)
        softwareMultiplier = bounded
        try {
            track?.setVolume(bounded.toFloat())
        } catch (error: IllegalStateException) {
            // The track may be released concurrently; the multiplier is still recorded.
        }
    }

    @Synchronized
    fun flushStream() {
        val active = track
        if (active != null) {
            try {
                active.pause()
            } catch (error: Exception) {
                // Ignore a track that lost its audio session.
            }
            try {
                active.flush()
            } catch (error: Exception) {
                // Ignore a track that lost its audio session.
            }
            platformFramesWritten = 0L
            try {
                if (active.state == AudioTrack.STATE_UNINITIALIZED) {
                    releaseTrack()
                } else {
                    active.play()
                }
            } catch (error: Exception) {
                releaseTrack()
            }
        } else {
            platformFramesWritten = 0L
        }
        discardFifo()
        fifoFramesPlayed = 0L
    }

    fun getFramesPlayed(): Long {
        val active = track ?: return fifoFramesPlayed
        return try {
            active.playbackHeadPosition.toLong() and 0xFFFFFFFFL
        } catch (error: Exception) {
            0L
        }
    }

    fun hasPendingData(): Boolean {
        synchronized(ringLock) {
            if (ringSize > 0) return true
        }
        val active = track ?: return false
        return try {
            platformFramesWritten > (active.playbackHeadPosition.toLong() and 0xFFFFFFFFL)
        } catch (error: Exception) {
            false
        }
    }

    private fun appendLocked(data: ByteArray, length: Int): Int {
        val accepted = minOf(length, FIFO_CAPACITY - ringSize)
        for (index in 0 until accepted) {
            ring[(ringHead + ringSize + index) % FIFO_CAPACITY] = data[index]
        }
        ringSize += accepted
        return accepted
    }

    private fun discardFifo() {
        synchronized(ringLock) {
            ringHead = 0
            ringSize = 0
        }
    }

    private fun pendingPlatformFrames(active: AudioTrack): Long {
        val played = active.playbackHeadPosition.toLong() and 0xFFFFFFFFL
        return (platformFramesWritten - played).coerceAtLeast(0L)
    }

    private fun openTrack(rate: Int, channelCount: Int): AudioTrack? {
        return try {
            val channelMask =
                if (channelCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
            val minimum = AudioTrack.getMinBufferSize(rate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
            val hundredMilliseconds = rate * frameSize / 10
            val bufferBytes = maxOf(minimum, hundredMilliseconds, frameSize)
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(rate)
                .setChannelMask(channelMask)
                .build()
            val created = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            created.play()
            if (created.state != AudioTrack.STATE_INITIALIZED) {
                created.release()
                null
            } else {
                created
            }
        } catch (error: Exception) {
            null
        }
    }

    private fun releaseTrack() {
        val active = track ?: return
        track = null
        try {
            active.pause()
        } catch (error: Exception) {
            // Best effort; the platform may already have invalidated the track.
        }
        try {
            active.flush()
        } catch (error: Exception) {
            // Best effort.
        }
        try {
            active.release()
        } catch (error: Exception) {
            // Best effort.
        }
    }

    private fun attenuate(data: ByteArray, bytesPerSample: Int, multiplier: Double) {
        if (bytesPerSample < 2 || bytesPerSample > 4) return
        val minimum = minimumValue(bytesPerSample)
        val maximum = maximumValue(bytesPerSample)
        var offset = 0
        while (offset + bytesPerSample <= data.size) {
            val value = readSample(data, offset, bytesPerSample)
            val scaled = (value * multiplier).coerceIn(minimum.toDouble(), maximum.toDouble()).toInt()
            writeSample(data, offset, bytesPerSample, scaled)
            offset += bytesPerSample
        }
    }

    private fun readSample(data: ByteArray, offset: Int, bytes: Int): Int = when (bytes) {
        2 -> (data[offset].toInt() and 0xFF) or (data[offset + 1].toInt() shl 8)
        3 -> (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            (data[offset + 2].toInt() shl 16)
        else -> (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun writeSample(data: ByteArray, offset: Int, bytes: Int, value: Int) {
        data[offset] = (value and 0xFF).toByte()
        if (bytes >= 2) data[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        if (bytes >= 3) data[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        if (bytes >= 4) data[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }

    private fun minimumValue(bytes: Int): Int = when (bytes) {
        2 -> -32768
        3 -> -8388608
        else -> Int.MIN_VALUE
    }

    private fun maximumValue(bytes: Int): Int = when (bytes) {
        2 -> 32767
        3 -> 8388607
        else -> Int.MAX_VALUE
    }
}
