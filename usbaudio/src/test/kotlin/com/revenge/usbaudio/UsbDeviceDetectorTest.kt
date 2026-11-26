package com.revenge.usbaudio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbDeviceDetectorTest {

    @Test
    fun audioClassPassThroughMirrorsArgument() {
        val detector = UsbDeviceDetector()
        assertTrue(detector.isAudioClassDevice(true))
        assertFalse(detector.isAudioClassDevice(false))
    }
}
