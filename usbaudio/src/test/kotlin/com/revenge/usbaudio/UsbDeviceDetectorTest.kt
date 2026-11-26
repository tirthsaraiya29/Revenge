package com.revenge.usbaudio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbDeviceDetectorTest {

  @Test
  fun audioInterfacePredicatePassesThrough() {
    val detector = UsbDeviceDetector()
    assertTrue(detector.isAudioClassDevice(hasAudioInterface = true))
    assertFalse(detector.isAudioClassDevice(hasAudioInterface = false))
  }
}
