@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.revenge.usbaudio

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.AudioSink
import com.revenge.usbaudio.model.DacCapabilities
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbDacAudioSinkTest {

    private fun rawFormat(rate: Int, channels: Int, encoding: Int): Format =
        Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setSampleRate(rate)
            .setChannelCount(channels)
            .setPcmEncoding(encoding)
            .build()

    private fun aacFormat(rate: Int): Format =
        Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setSampleRate(rate)
            .setChannelCount(2)
            .build()

    private fun emptyBuffer(bytes: Int): ByteBuffer =
        ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())

    @Test
    fun supportsAdvertisedRatesAndRejectsUnsupportedFormats() {
        val sink = UsbDacAudioSink(
            UsbAudioDriver(),
            DacCapabilities(uacVersion = 2, supportedSampleRates = listOf(48_000, 96_000)),
        )

        assertTrue(sink.supportsFormat(rawFormat(96_000, 2, C.ENCODING_PCM_24BIT)))
        assertFalse(sink.supportsFormat(rawFormat(384_000, 2, C.ENCODING_PCM_16BIT)))
        assertFalse(sink.supportsFormat(rawFormat(96_000, 2, C.ENCODING_PCM_FLOAT)))
        assertFalse(sink.supportsFormat(aacFormat(96_000)))
    }

    @Test
    fun withoutAdvertisedRatesFallbackIsCappedAt192k() {
        val sink = UsbDacAudioSink(UsbAudioDriver())

        assertTrue(sink.supportsFormat(rawFormat(192_000, 2, C.ENCODING_PCM_16BIT)))
        assertFalse(sink.supportsFormat(rawFormat(384_000, 2, C.ENCODING_PCM_16BIT)))

        assertEquals(
            AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY,
            sink.getFormatSupport(rawFormat(96_000, 2, C.ENCODING_PCM_16BIT)),
        )
        assertEquals(
            AudioSink.SINK_FORMAT_UNSUPPORTED,
            sink.getFormatSupport(aacFormat(96_000)),
        )
    }

    @Test
    fun positionTracksWrittenFramesUntilEndOfStream() {
        val driver = UsbAudioDriver()
        val sink = UsbDacAudioSink(driver)
        sink.configure(rawFormat(48_000, 2, C.ENCODING_PCM_16BIT), 0, null)
        sink.play()

        val buffer = emptyBuffer(1920)
        assertTrue(sink.handleBuffer(buffer, 1_000_000L, 0))
        assertEquals(1_010_000L, sink.getCurrentPositionUs(false))
        assertFalse(sink.isEnded())

        sink.playToEndOfStream()
        assertFalse(sink.isEnded())
        assertTrue(sink.hasPendingData())

        sink.flush()
        assertEquals(AudioSink.CURRENT_POSITION_NOT_SET, sink.getCurrentPositionUs(false))
        assertFalse(sink.hasPendingData())
    }

    @Test
    fun transportBackpressureConsumesOnlyAvailableCapacity() {
        val driver = UsbAudioDriver()
        val sink = UsbDacAudioSink(driver)
        sink.configure(rawFormat(48_000, 2, C.ENCODING_PCM_16BIT), 0, null)
        sink.play()

        val buffer = emptyBuffer(140_000)
        assertFalse(sink.handleBuffer(buffer, C.TIME_UNSET, 0))
        assertEquals(131_072, buffer.position())

        assertFalse(sink.handleBuffer(buffer, C.TIME_UNSET, 0))
        assertEquals(131_072, buffer.position())

        sink.flush()
        assertTrue(sink.handleBuffer(buffer, 0L, 0))
        assertEquals(140_000, buffer.position())
    }

    @Test
    fun resetStopsPlaybackAndClearsPosition() {
        val driver = UsbAudioDriver()
        val sink = UsbDacAudioSink(driver)
        sink.configure(rawFormat(48_000, 2, C.ENCODING_PCM_16BIT), 0, null)
        sink.play()
        assertTrue(sink.handleBuffer(emptyBuffer(400), 0L, 0))
        assertTrue(sink.hasPendingData())

        sink.reset()

        assertFalse(sink.hasPendingData())
        assertFalse(sink.isEnded())
        assertEquals(AudioSink.CURRENT_POSITION_NOT_SET, sink.getCurrentPositionUs(false))
    }
}
