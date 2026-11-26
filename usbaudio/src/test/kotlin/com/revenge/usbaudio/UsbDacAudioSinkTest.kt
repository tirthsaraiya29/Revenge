package com.revenge.usbaudio

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.AudioSink
import com.revenge.usbaudio.model.DacCapabilities
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbDacAudioSinkTest {

  private fun rawFormat(
    sampleRate: Int = 48_000,
    channels: Int = 2,
    encoding: Int = C.ENCODING_PCM_16BIT,
  ): Format =
    Format.Builder()
      .setSampleMimeType(MimeTypes.AUDIO_RAW)
      .setChannelCount(channels)
      .setSampleRate(sampleRate)
      .setPcmEncoding(encoding)
      .build()

  @Test
  fun supportsOnlyAdvertisedRawFormats() {
    val capabilities =
      DacCapabilities(
        uacVersion = 2,
        supportedSampleRates = listOf(44_100, 48_000, 88_200, 96_000, 176_400, 192_000),
      )
    val sink = UsbDacAudioSink(UsbAudioDriver(), capabilities = capabilities)

    assertTrue(sink.supportsFormat(rawFormat(96_000, encoding = C.ENCODING_PCM_24BIT)))
    assertFalse(sink.supportsFormat(rawFormat(384_000)))
    assertFalse(sink.supportsFormat(rawFormat(encoding = C.ENCODING_PCM_FLOAT)))

    val nonRaw = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()
    assertFalse(sink.supportsFormat(nonRaw))
  }

  @Test
  fun acceptsAnyRateWhenDacAdvertisesNone() {
    val sink = UsbDacAudioSink(UsbAudioDriver(), capabilities = DacCapabilities(uacVersion = 1))

    assertTrue(sink.supportsFormat(rawFormat(192_000)))
    assertFalse(sink.supportsFormat(rawFormat(384_000)))
    assertEquals(
      AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY,
      sink.getFormatSupport(rawFormat(48_000)),
    )
  }

  @Test
  fun bufferAdvancesClockAndFlushRewindsIt() {
    val driver = UsbAudioDriver()
    val sink = UsbDacAudioSink(driver)
    sink.configure(rawFormat(), 0, null)

    assertFalse(sink.isEnded())
    sink.play()

    // 480 frames of stereo 16-bit PCM is 10 ms at 48 kHz.
    val buffer = ByteBuffer.allocate(480 * 4)
    buffer.put(ByteArray(480 * 4))
    buffer.flip()

    assertTrue(sink.handleBuffer(buffer, 1_000_000L, 1))
    assertEquals(1_010_000L, sink.getCurrentPositionUs(false))
    assertFalse(sink.isEnded())

    sink.playToEndOfStream()
    assertFalse(sink.isEnded()) // Data is still queued in the transport.

    sink.flush()
    assertEquals(
      AudioSink.CURRENT_POSITION_NOT_SET,
      sink.getCurrentPositionUs(false),
    )
    assertFalse(sink.hasPendingData())
  }

  @Test
  fun fullTransportAppliesBackpressureAndKeepsBufferPosition() {
    val driver = UsbAudioDriver()
    val sink = UsbDacAudioSink(driver)
    sink.configure(rawFormat(), 0, null)
    sink.play()

    val capacity = driver.getAvailableWrite()
    val totalFrames = capacity / 4 + 1_000
    val buffer = ByteBuffer.allocate(totalFrames * 4)
    buffer.position(buffer.limit())
    buffer.flip()

    assertFalse(sink.handleBuffer(buffer, 0L, totalFrames))
    assertEquals(capacity, buffer.position())

    // Transport is full, so no progress is possible until data is drained.
    assertFalse(sink.handleBuffer(buffer, 0L, 1))
    assertEquals(capacity, buffer.position())

    sink.flush()
    assertTrue(sink.handleBuffer(buffer, 0L, 1))
    assertEquals(buffer.limit(), buffer.position())
  }

  @Test
  fun resetStopsPlaybackAndClearsPendingData() {
    val driver = UsbAudioDriver()
    val sink = UsbDacAudioSink(driver)
    sink.configure(rawFormat(), 0, null)
    sink.play()

    val buffer = ByteBuffer.allocate(16)
    assertTrue(sink.handleBuffer(buffer, 0L, 1))
    assertTrue(sink.hasPendingData())

    sink.reset()
    assertFalse(sink.hasPendingData())
    assertEquals(AudioSink.CURRENT_POSITION_NOT_SET, sink.getCurrentPositionUs(false))
  }
}
