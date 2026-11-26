package com.revenge.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

@UnstableApi
class SilenceDetectorAudioProcessorTest {

    @Test
    fun configureAcceptsPcm16BitAndReturnsSameFormat() {
        val processor = processor()
        val format = AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT)

        assertSame(format, processor.configure(format))
    }

    @Test
    fun configureRejectsOtherEncodings() {
        val processor = processor()
        val format = AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_FLOAT)

        assertThrows(AudioProcessor.UnhandledAudioFormatException::class.java) {
            processor.configure(format)
        }
    }

    @Test
    fun instantModeOffPassesAudioWithoutCallbacks() {
        val callbacks = Callbacks()
        val processor = processor(callbacks)
        val samples = ShortArray(3_000)

        assertArrayEquals(pcmBytes(samples), drain(processor, samples))

        assertEquals(0, callbacks.count)
        assertFalse(processor.isCurrentlySilent())
    }

    @Test
    fun longSilenceReportsOncePerRun() {
        val callbacks = Callbacks()
        val processor = processor(callbacks)
        processor.instantModeEnabled = true

        drain(processor, ShortArray(1_500))
        assertEquals(1, callbacks.count)
        assertTrue(processor.isCurrentlySilent())

        drain(processor, ShortArray(1_500))
        assertEquals(1, callbacks.count)
    }

    @Test
    fun loudFrameResetsRunAndLetsLaterSilenceReportAgain() {
        val callbacks = Callbacks()
        val processor = processor(callbacks)
        processor.instantModeEnabled = true

        drain(processor, ShortArray(1_500))
        drain(processor, ShortArray(1) { 1_000.toShort() })
        assertFalse(processor.isCurrentlySilent())

        drain(processor, ShortArray(1_500))
        assertEquals(2, callbacks.count)
    }

    @Test
    fun thresholdIsStrict() {
        val atThreshold = Callbacks()
        val strictProcessor = processor(atThreshold)
        strictProcessor.instantModeEnabled = true
        drain(strictProcessor, ShortArray(2_000) { 256.toShort() })
        assertFalse(strictProcessor.isCurrentlySilent())
        assertEquals(0, atThreshold.count)

        val belowThreshold = Callbacks()
        val belowProcessor = processor(belowThreshold)
        belowProcessor.instantModeEnabled = true
        drain(belowProcessor, ShortArray(2_000) { 255.toShort() })
        assertTrue(belowProcessor.isCurrentlySilent())
        assertEquals(1, belowThreshold.count)
    }

    @Test
    fun peakIsTakenAcrossChannels() {
        val callbacks = Callbacks()
        val processor = processor(callbacks, channels = 2)
        processor.instantModeEnabled = true

        val interleaved = ShortArray(4_000) { index -> if (index % 2 == 0) 0.toShort() else 1_000.toShort() }
        drainInterleaved(processor, interleaved)

        assertFalse(processor.isCurrentlySilent())
        assertEquals(0, callbacks.count)
    }

    @Test
    fun flushAndResetTrackingAllowANewReport() {
        val callbacks = Callbacks()
        val processor = processor(callbacks)
        processor.instantModeEnabled = true
        drain(processor, ShortArray(1_500))
        assertEquals(1, callbacks.count)

        processor.flush()
        drain(processor, ShortArray(1_500))
        assertEquals(2, callbacks.count)

        processor.resetTracking()
        drain(processor, ShortArray(1_500))
        assertEquals(3, callbacks.count)
    }

    @Test
    fun outputIsByteForByteIdenticalInBothModes() {
        val samples = ShortArray(2_048) { (it * 37 % 4000 - 2000).toShort() }
        val expected = pcmBytes(samples)

        val plain = processor()
        assertArrayEquals(expected, drain(plain, samples))

        val scanning = processor()
        scanning.instantModeEnabled = true
        assertArrayEquals(expected, drain(scanning, samples))
    }

    @Test
    fun emptyInputProducesEmptyOutputAndEndsOnlyAfterQueueEnd() {
        val processor = processor()
        val empty = ByteBuffer.allocateDirect(0)

        processor.queueInput(empty)
        val output = processor.getOutput()
        assertEquals(0, output.remaining())
        assertFalse(processor.isEnded())

        processor.queueEndOfStream()
        assertTrue(processor.isEnded())
    }

    @Test
    fun disablingInstantModeMidSilenceResetsState() {
        val callbacks = Callbacks()
        val processor = processor(callbacks)
        processor.instantModeEnabled = true
        drain(processor, ShortArray(1_500))
        assertEquals(1, callbacks.count)

        processor.instantModeEnabled = false
        drain(processor, ShortArray(1_500))

        assertEquals(1, callbacks.count)
        assertFalse(processor.isCurrentlySilent())
    }

    private fun processor(
        callbacks: Callbacks = Callbacks(),
        channels: Int = 1,
    ): SilenceDetectorAudioProcessor {
        val processor = SilenceDetectorAudioProcessor(
            minSilenceDurationUs = 1_000_000L,
            silenceThreshold = 256,
            onLongSilence = { callbacks.count++ },
        )
        processor.configure(AudioProcessor.AudioFormat(1_000, channels, C.ENCODING_PCM_16BIT))
        return processor
    }

    private fun drain(processor: SilenceDetectorAudioProcessor, samples: ShortArray): ByteArray {
        processor.queueInput(pcmBuffer(samples))
        return drainOutput(processor)
    }

    private fun drainInterleaved(processor: SilenceDetectorAudioProcessor, samples: ShortArray) {
        processor.queueInput(pcmBuffer(samples))
        drainOutput(processor)
    }

    private fun drainOutput(processor: SilenceDetectorAudioProcessor): ByteArray {
        val buffer = processor.getOutput()
        val copy = buffer.duplicate()
        val bytes = ByteArray(copy.remaining())
        copy.get(bytes)
        return bytes
    }

    private fun pcmBuffer(samples: ShortArray): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder())
        for (sample in samples) buffer.putShort(sample)
        buffer.flip()
        return buffer
    }

    private fun pcmBytes(samples: ShortArray): ByteArray {
        val buffer = pcmBuffer(samples)
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return bytes
    }

    private class Callbacks {
        var count: Int = 0
    }
}
