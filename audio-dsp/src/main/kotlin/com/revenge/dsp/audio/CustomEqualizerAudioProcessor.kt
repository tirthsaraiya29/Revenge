package com.revenge.dsp.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.revenge.dsp.core.BiquadFilter
import com.revenge.dsp.core.models.ParametricEQ
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

/**
 * Parametric EQ stage. The last requested profile is remembered; a profile that arrives before a
 * sample rate is configured is installed the moment [configure] succeeds.
 */
@UnstableApi
class CustomEqualizerAudioProcessor : AudioProcessor {

    private var requestedProfile: ParametricEQ? = null
    private val bands = ArrayList<BiquadFilter>()

    @Volatile
    private var preampScale: Double = 1.0

    @Volatile
    private var stageInstalled: Boolean = false

    @Volatile
    private var configuredRate: Int = 0

    @Volatile
    private var configuredChannels: Int = 0

    private var produced: ByteBuffer = blankBuffer()
    private var ended: Boolean = false

    @Synchronized
    fun applyProfile(parametricEQ: ParametricEQ) {
        requestedProfile = parametricEQ
        if (configuredRate > 0) {
            buildStage(parametricEQ)
        }
    }

    @Synchronized
    fun disable() {
        requestedProfile = null
        bands.clear()
        preampScale = 1.0
        stageInstalled = false
    }

    fun isEnabled(): Boolean = stageInstalled

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount > 2) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        configuredRate = inputAudioFormat.sampleRate
        configuredChannels = inputAudioFormat.channelCount
        requestedProfile?.let(::buildStage)
        return inputAudioFormat
    }

    override fun isActive(): Boolean = configuredRate > 0

    override fun queueInput(inputBuffer: ByteBuffer) {
        val available = inputBuffer.remaining()
        if (available == 0) return

        val channels = configuredChannels
        val rate = configuredRate
        val applyStage = rate > 0 && stageInstalled && (bands.isNotEmpty() || preampScale != 1.0)
        val destination = ByteBuffer.allocateDirect(available).order(ByteOrder.nativeOrder())
        val source = inputBuffer.duplicate().order(inputBuffer.order())

        if (!applyStage || channels <= 0) {
            val untouched = ByteArray(available)
            source.get(untouched)
            destination.put(untouched)
        } else if (channels == 1) {
            while (source.remaining() >= SAMPLE_BYTES) {
                var sample = source.getShort() / PCM_SCALE
                for (band in bands) {
                    sample = band.processSample(sample)
                }
                destination.putShort(toPcm16(sample * preampScale))
            }
        } else {
            while (source.remaining() >= 2 * SAMPLE_BYTES) {
                var left = source.getShort() / PCM_SCALE
                var right = source.getShort() / PCM_SCALE
                for (band in bands) {
                    val filtered = band.processStereo(left, right)
                    left = filtered.first
                    right = filtered.second
                }
                destination.putShort(toPcm16(left * preampScale))
                destination.putShort(toPcm16(right * preampScale))
            }
        }

        inputBuffer.position(inputBuffer.limit())
        destination.flip()
        produced = destination
    }

    override fun getOutput(): ByteBuffer {
        val delivered = produced
        produced = blankBuffer()
        return delivered
    }

    override fun isEnded(): Boolean = ended && !produced.hasRemaining()

    override fun queueEndOfStream() {
        ended = true
    }

    override fun flush() {
        produced = blankBuffer()
        ended = false
        bands.forEach(BiquadFilter::reset)
    }

    override fun reset() {
        produced = blankBuffer()
        ended = false
        configuredRate = 0
        configuredChannels = 0
        bands.clear()
        preampScale = 1.0
        stageInstalled = false
    }

    private fun buildStage(profile: ParametricEQ) {
        bands.clear()
        val nyquist = configuredRate / 2.0
        for (band in profile.bands) {
            if (band.enabled && band.frequency > 0.0 && band.frequency < nyquist) {
                bands.add(BiquadFilter(configuredRate, band.frequency, band.gain, band.q, band.filterType))
            }
        }
        preampScale = 10.0.pow(profile.preamp / 20.0)
        stageInstalled = true
    }

    private fun toPcm16(normalised: Double): Short {
        val bounded = (normalised * PCM_SCALE).coerceIn(PCM_MIN, PCM_MAX)
        return bounded.toInt().toShort()
    }

    private fun blankBuffer(): ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

    private companion object {
        const val SAMPLE_BYTES = 2
        const val PCM_SCALE = 32768.0
        const val PCM_MIN = -32768.0
        const val PCM_MAX = 32767.0
    }
}
