package com.revenge.usbaudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DescriptorParserTest {

  private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

  private fun concat(vararg chunks: ByteArray): ByteArray {
    val total = chunks.sumOf { it.size }
    val result = ByteArray(total)
    var cursor = 0
    for (chunk in chunks) {
      chunk.copyInto(result, cursor)
      cursor += chunk.size
    }
    return result
  }

  private fun rate(value: Int): ByteArray =
    bytes(value and 0xFF, (value ushr 8) and 0xFF, (value ushr 16) and 0xFF)

  private fun uac1Fixture(): ByteArray =
    concat(
      bytes(9, 0x04, 0, 0, 0, 0x01, 0x01, 0x00, 0),
      bytes(6, 0x24, 0x06, 2, 0, 0),
      bytes(9, 0x04, 1, 1, 1, 0x01, 0x02, 0x00, 0),
      bytes(14, 0x24, 0x02, 0x01, 2, 3, 24, 2) + rate(44100) + rate(48000),
      bytes(7, 0x05, 0x01, 0x01, 0, 0, 1),
    )

  private fun continuousRangeFixture(): ByteArray =
    concat(
      bytes(9, 0x04, 0, 0, 0, 0x01, 0x01, 0x00, 0),
      bytes(9, 0x04, 1, 1, 1, 0x01, 0x02, 0x00, 0),
      bytes(14, 0x24, 0x02, 0x01, 2, 3, 24, 0) + rate(44100) + rate(96000),
    )

  private fun uac2ClockFixture(): ByteArray =
    concat(
      bytes(9, 0x04, 0, 0, 0, 0x01, 0x01, 0x20, 0),
      bytes(8, 0x24, 0x0A, 1, 0, 0, 0, 0),
      bytes(9, 0x04, 1, 1, 1, 0x01, 0x02, 0x00, 0),
      bytes(11, 0x24, 0x01, 0, 0, 0, 0, 0, 0, 0, 2),
      bytes(7, 0x24, 0x02, 0x01, 4, 24, 0),
      bytes(7, 0x05, 0x01, 0x01, 0, 0, 1),
    )

  @Test
  fun uac1FixtureIsParsedCompletely() {
    val capabilities = UsbDescriptorParser.parse(uac1Fixture())

    assertEquals(1, capabilities.uacVersion)
    assertTrue(capabilities.hasHardwareVolume)
    assertEquals(2, capabilities.volumeFeatureUnitId)
    assertEquals(listOf(44100, 48000), capabilities.supportedSampleRates)
    assertEquals(1, capabilities.supportedFormats.size)

    val format = capabilities.supportedFormats.first()
    assertEquals(1, format.interfaceNumber)
    assertEquals(1, format.altSetting)
    assertEquals(0x01, format.endpointAddress)
    assertEquals(24, format.bitDepth)
    assertEquals(3, format.subslotBytes)
    assertEquals(2, format.channels)
    assertEquals(listOf(44100, 48000), format.sampleRates)
    assertNull(format.syncEndpointAddress)
  }

  @Test
  fun continuousRangeUsesStandardRatesInsideBounds() {
    val capabilities = UsbDescriptorParser.parse(continuousRangeFixture())

    assertEquals(listOf(44100, 48000, 88200, 96000), capabilities.supportedSampleRates)
  }

  @Test
  fun uac2ClockSourceSeedsFortyEightKhz() {
    val capabilities = UsbDescriptorParser.parse(uac2ClockFixture())

    assertEquals(2, capabilities.uacVersion)
    assertEquals(1, capabilities.clockSourceId)
    assertTrue(capabilities.supportedSampleRates.contains(48000))
    assertEquals(1, capabilities.supportedFormats.size)
    assertTrue(capabilities.supportedFormats.first().sampleRates.contains(48000))
  }

  @Test
  fun malformedAndEmptyInputYieldsUac1Defaults() {
    val inputs =
      listOf(
        ByteArray(0),
        bytes(0x04),
        bytes(0x04, 0x04),
        bytes(2, 0x24, 0x01),
      )
    for (input in inputs) {
      val capabilities = UsbDescriptorParser.parse(input)
      assertEquals(1, capabilities.uacVersion)
      assertTrue(capabilities.supportedFormats.isEmpty())
      assertTrue(capabilities.supportedSampleRates.isEmpty())
      assertNull(capabilities.clockSourceId)
    }
  }
}
