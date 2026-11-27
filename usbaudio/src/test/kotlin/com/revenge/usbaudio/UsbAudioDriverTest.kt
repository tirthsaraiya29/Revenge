package com.revenge.usbaudio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbAudioDriverTest {

  @Test
  fun identityAndVersionAreStable() {
    val driver = UsbAudioDriver()
    assertTrue(driver.isLoaded())
    assertEquals("1.0.0-usbaudio", driver.getNativeDriverVersion())
    assertFalse(UsbAudioDriver.nativeLoaded)
  }

  @Test
  fun ringBufferPreservesByteOrderAcrossPartialReads() {
    val driver = UsbAudioDriver()
    val payload = ByteArray(10) { it.toByte() }
    assertEquals(10, driver.testRingBufferWrite(payload))

    assertArrayEquals(payload.copyOfRange(0, 4), driver.testRingBufferRead(4))
    val more = ByteArray(4) { (10 + it).toByte() }
    assertEquals(4, driver.testRingBufferWrite(more))

    val expected = ByteArray(10) { (4 + it).toByte() }
    assertArrayEquals(expected, driver.testRingBufferRead(10))
  }

  @Test
  fun drainedRingReportsNoPendingData() {
    val driver = UsbAudioDriver()
    driver.testRingBufferWrite(ByteArray(6) { 7.toByte() })
    assertEquals(6, driver.testRingBufferRead(6).size)
    assertFalse(driver.hasPendingData())
  }

  @Test
  fun ringReadsClampToAvailableBytes() {
    val driver = UsbAudioDriver()
    driver.testRingBufferWrite(byteArrayOf(1, 2, 3))

    assertEquals(3, driver.testRingBufferRead(10).size)
    assertEquals(0, driver.testRingBufferRead(5).size)
  }

  @Test
  fun availableWriteShrinksAndRestoresAfterFlush() {
    val driver = UsbAudioDriver()
    val initial = driver.getAvailableWrite()
    assertEquals(131_072, initial)

    driver.testRingBufferWrite(ByteArray(1000))
    assertEquals(initial - 1000, driver.getAvailableWrite())

    driver.testRingBufferFlush()
    assertEquals(initial, driver.getAvailableWrite())
  }

  @Test
  fun nominalPacketSamplesUseEightKilohertzFrames() {
    val driver = UsbAudioDriver()
    assertEquals(12, driver.calculateNominalPacketSamples(96_000))
    assertEquals(5, driver.calculateNominalPacketSamples(44_100))
    assertEquals(0, driver.calculateNominalPacketSamples(0))
  }

  @Test
  fun streamingThroughFifoCountsFramesAndFlushes() {
    val driver = UsbAudioDriver()
    assertEquals(0, driver.startStream(fd = -1, dataEp = 1, sampleRate = 48_000))

    assertEquals(1920, driver.writeAudio(ByteArray(1920)))
    assertEquals(480L, driver.getFramesPlayed())
    assertTrue(driver.hasPendingData())

    driver.flushStream()
    assertEquals(0L, driver.getFramesPlayed())
    assertFalse(driver.hasPendingData())
    assertEquals(131_072, driver.getAvailableWrite())
  }

  @Test
  fun softwareVolumeAttenuatesSixteenBitSamples() {
    val driver = UsbAudioDriver()
    driver.startStream(fd = -1, dataEp = 1, sampleRate = 48_000, bitDepth = 16, channels = 1)
    driver.setSoftwareVolumeMultiplier(0.5)

    val sample = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(1000).array()
    assertEquals(2, driver.writeAudio(sample))

    val read = driver.testRingBufferRead(2)
    val value = (read[0].toInt() and 0xFF) or (read[1].toInt() shl 8)
    assertEquals(500, value)
  }

  @Test
  fun emptyWritesAreRejected() {
    val driver = UsbAudioDriver()
    assertEquals(0, driver.writeAudio(ByteArray(0)))
    assertEquals(0, driver.writeAudio(ByteArray(4), 0))
  }
}
