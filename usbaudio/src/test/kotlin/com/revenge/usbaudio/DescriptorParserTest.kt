package com.revenge.usbaudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DescriptorParserTest {

  @Test
  fun uac2ClockSourceAdvertisesDefaultRate() {
    val descriptors =
      byteArrayOf(
        // Configuration.
        0x09, 0x02, 0x64, 0x00, 0x02, 0x01, 0x00, 0xC0.toByte(), 0x32,
        // AudioControl interface, UAC 2 protocol.
        0x09, 0x04, 0x00, 0x00, 0x00, 0x01, 0x01, 0x20, 0x00,
        // AC header.
        0x09, 0x24, 0x01, 0x00, 0x02, 0x01, 0x30, 0x00, 0x00,
        // Clock source id 1.
        0x08, 0x24, 0x0A, 0x01, 0x03, 0x07, 0x00, 0x00,
        // AudioStreaming interface, alternate setting 1.
        0x09, 0x04, 0x01, 0x01, 0x02, 0x01, 0x02, 0x20, 0x00,
      )

    val capabilities = UsbAudioDriver().parseDescriptors(descriptors)

    assertEquals(2, capabilities.uacVersion)
    assertEquals(1, capabilities.clockSourceId)
    assertTrue(capabilities.supportedSampleRates.contains(48_000))
    assertEquals(1, capabilities.supportedFormats.size)
    assertTrue(capabilities.supportedFormats.first().sampleRates.contains(48_000))
  }

  @Test
  fun uac1FormatTypeOneParsesDepthChannelsAndRates() {
    val descriptors =
      byteArrayOf(
        // Configuration.
        0x09, 0x02, 0x64, 0x00, 0x01, 0x01, 0x00, 0x80.toByte(), 0x32,
        // AudioControl interface, UAC 1 protocol.
        0x09, 0x04, 0x00, 0x00, 0x00, 0x01, 0x01, 0x00, 0x00,
        // AC header.
        0x09, 0x24, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00,
        // Feature unit id 2 => hardware volume.
        0x07, 0x24, 0x06, 0x02, 0x01, 0x00, 0x00,
        // AudioStreaming interface, alt 1, one endpoint.
        0x09, 0x04, 0x01, 0x01, 0x01, 0x01, 0x02, 0x00, 0x00,
        // AS general.
        0x07, 0x24, 0x01, 0x01, 0x00, 0x00, 0x00,
        // Format Type I: 2 channels, 3-byte subslots, 24 bits, two discrete rates.
        0x0E, 0x24, 0x02, 0x01, 0x02, 0x03, 0x18, 0x02,
        0x44, 0xAC.toByte(), 0x00,
        0x80.toByte(), 0xBB.toByte(), 0x00,
        // Isochronous OUT endpoint 0x01.
        0x07, 0x05, 0x01, 0x09, 0x60, 0x00, 0x01,
      )

    val capabilities = UsbAudioDriver().parseDescriptors(descriptors)

    assertEquals(1, capabilities.uacVersion)
    assertTrue(capabilities.hasHardwareVolume)
    assertEquals(2, capabilities.volumeFeatureUnitId)
    assertEquals(listOf(44_100, 48_000), capabilities.supportedSampleRates)

    val format = capabilities.supportedFormats.single()
    assertEquals(1, format.interfaceNumber)
    assertEquals(1, format.altSetting)
    assertEquals(0x01, format.endpointAddress)
    assertEquals(24, format.bitDepth)
    assertEquals(3, format.subslotBytes)
    assertEquals(2, format.channels)
    assertEquals(listOf(44_100, 48_000), format.sampleRates)
    assertNull(format.syncEndpointAddress)
  }

  @Test
  fun continuousUac1RangeExpandsToStandardRates() {
    val descriptors =
      byteArrayOf(
        0x09, 0x04, 0x00, 0x00, 0x00, 0x01, 0x01, 0x00, 0x00,
        0x09, 0x04, 0x01, 0x01, 0x00, 0x01, 0x02, 0x00, 0x00,
        // 16-bit stereo, continuous 44.1 kHz .. 96 kHz.
        0x0E, 0x24, 0x02, 0x01, 0x02, 0x02, 0x10, 0x00,
        0x44, 0xAC.toByte(), 0x00,
        0x80.toByte(), 0x77, 0x01,
      )

    val capabilities = UsbAudioDriver().parseDescriptors(descriptors)

    assertEquals(listOf(44_100, 48_000, 88_200, 96_000), capabilities.supportedSampleRates)
  }

  @Test
  fun malformedAndTruncatedDescriptorsStopSafely() {
    val descriptors =
      byteArrayOf(
        0x02, 0x24, // CS_INTERFACE without a subtype.
        0x03, 0x24, 0x02, // Format type with no payload.
        0x09, 0x04, 0x01, // Interface truncated below nine bytes.
        0x00, // Zero-length terminator.
      )

    val capabilities = UsbAudioDriver().parseDescriptors(descriptors)

    assertEquals(1, capabilities.uacVersion)
    assertTrue(capabilities.supportedFormats.isEmpty())
    assertTrue(capabilities.supportedSampleRates.isEmpty())
  }

  @Test
  fun emptyDescriptorsYieldUac1Defaults() {
    val capabilities = UsbAudioDriver().parseDescriptors(ByteArray(0))

    assertEquals(1, capabilities.uacVersion)
    assertTrue(capabilities.supportedFormats.isEmpty())
    assertNull(capabilities.clockSourceId)
  }
}
