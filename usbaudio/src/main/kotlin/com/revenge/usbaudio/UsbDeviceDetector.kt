package com.revenge.usbaudio

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice

class UsbDeviceDetector {

    fun isAudioClassDevice(hasAudioInterface: Boolean): Boolean = hasAudioInterface

    fun isUsbAudioDevice(device: UsbDevice): Boolean {
        for (interfaceIndex in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(interfaceIndex)
            if (usbInterface.interfaceClass != UsbConstants.USB_CLASS_AUDIO) continue
            if (usbInterface.interfaceSubclass != AUDIO_STREAMING_SUBCLASS) continue
            for (endpointIndex in 0 until usbInterface.endpointCount) {
                val endpoint = usbInterface.getEndpoint(endpointIndex)
                if (endpoint.direction == UsbConstants.USB_DIR_OUT) return true
            }
        }
        return false
    }

    private companion object {
        const val AUDIO_STREAMING_SUBCLASS = 2
    }
}
