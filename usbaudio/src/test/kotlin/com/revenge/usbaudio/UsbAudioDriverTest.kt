package com.revenge.usbaudio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbAudioDriverTest {

  @Test
  fun driverIsAlwaysUsableInKotlinBuild() {
    val driver = UsbAudioDriver()
    assertTrue(driver.isLoaded())
    assertEquals("1.0.0-usbaudio", driver.getNativeDriverVersion())
    assertFalse(UsbAudioDriver.nativeLoaded)
  }

  @Test
  fun ringBufferPreservesOrderAcrossPartialReads() {
    val driver = UsbAudioDriver()
    val payload = ByteArray(16) { it.toByte() }

    assertEquals(payload.size, driver.testRingBufferWrite(payload))
    assertArrayEquals(payload, driver.testRingBufferRead(payload.size))

    // Write, partially read, then write again to force the indices to wrap.
    assertEquals(10, driver.testRingBufferWrite(ByteArray(10) { (it + 100).toByte() }))
    assertArrayEquals(ByteArray(4) { (it + 100).toByte() }, driver.testRingBufferRead(4))
    assertEquals(10, driver.testRingBufferWrite(ByteArray(10) { (it + 20).toByte() }))

    val expected = ByteArray(16)
    for (i in 0 until 6) expected[i] = (i + 4 + 100).toByte()
    for (i in 0 until 10) expected[6 + i] = (i + 20).toByte()
    assertArrayEquals(expected, driver.testRingBufferRead(16))
    assertFalse(driver.hasPendingData())
  }

  @Test
  fun readIsClampedToAvailableBytes() {
    val driver = UsbAudioDriver()
    driver.testRingBufferWrite(byteArrayOf(1, 2, 3))
    assertArrayEquals(byteArrayOf(1, 2), driver.testRingBufferRead(2))
    assertArrayEquals(byteArrayOf(3), driver.testRingBufferRead(32))
    assertArrayEquals(ByteArray(0), driver.testRingBufferRead(1))
  }

  @Test
  fun availableWriteShrinksAsDataIsQueued() {
    val driver = UsbAudioDriver()
    val capacity = driver.getAvailableWrite()
    driver.testRingBufferWrite(ByteArray(8))
    assertEquals(capacity - 8, driver.getAvailableWrite())
    driver.testRingBufferFlush()
    assertEquals(capacity, driver.getAvailableWrite())
  }

  @Test
  fun nominalPacketSamplesUseHighSpeedMicroframes() {
    val driver = UsbAudioDriver()
    assertEquals(12, driver.calculateNominalPacketSamples(96_000))
    assertEquals(5, driver.calculateNominalPacketSamples(44_100))
    assertEquals(0, driver.calculateNominalPacketSamples(0))
  }

  @Test
  fun writeAudioAdvancesFrameClockAndFlushResetsIt() {
    val driver = UsbAudioDriver()
    assertEquals(0, driver.startStream(fd = -1, dataEp = 1, sampleRate = 48_000))
    // 480 stereo 16-bit frames.
    val written = driver.writeAudio(ByteArray(480 * 4))
    assertEquals(480 * 4, written)
    assertEquals(480L, driver.getFramesPlayed())
    assertTrue(driver.hasPendingData())

    driver.flushStream()
    assertEquals(0L, driver.getFramesPlayed())
    assertFalse(driver.hasPendingData())
  }

  @Test
  fun softwareVolumeAttenuatesSixteenBitSamples() {
    val driver = UsbAudioDriver()
    driver.startStream(fd = -1, dataEp = 1, sampleRate = 48_000, bitDepth = 16, channels = 1)
    driver.setSoftwareVolumeMultiplier(0.5)

    val pcm = byteArrayOf(0xE8.toByte(), 0x03) // 1000
    assertEquals(2, driver.writeAudio(pcm))
    val converted = driver.testRingBufferRead(2)
    val value = ((converted[0].toInt() and 0xFF) or (converted[1].toInt() shl 8)).toShort()
    assertEquals(500, value.toInt())
  }

  @Test
  fun writeAudioRejectsEmptyInput() {
    val driver = UsbAudioDriver()
    assertEquals(0, driver.writeAudio(ByteArray(0)))
    assertEquals(0, driver.writeAudio(byteArrayOf(1, 2), 0))
  }
}
