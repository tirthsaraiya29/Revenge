package com.revenge.usbaudio

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice

/** Recognises USB devices that can carry audio to a DAC. */
class UsbDeviceDetector {

  private companion object {
    const val AUDIO_CLASS = 1
    const val AUDIO_STREAMING_SUBCLASS = 2
  }

  /** Convenience for callers that already know whether an audio interface was found. */
  fun isAudioClassDevice(hasAudioInterface: Boolean): Boolean = hasAudioInterface

  /**
   * True when [device] exposes an AudioStreaming interface with an OUT (playback) endpoint.
   * Audio Control-only interfaces (microphones, MIDI) are rejected.
   */
  fun isUsbAudioDevice(device: UsbDevice): Boolean {
    for (interfaceIndex in 0 until device.interfaceCount) {
      val usbInterface = device.getInterface(interfaceIndex)
      if (
        usbInterface.interfaceClass != AUDIO_CLASS ||
          usbInterface.interfaceSubclass != AUDIO_STREAMING_SUBCLASS
      ) {
        continue
      }
      for (endpointIndex in 0 until usbInterface.endpointCount) {
        val endpoint = usbInterface.getEndpoint(endpointIndex)
        if (endpoint.direction == UsbConstants.USB_DIR_OUT) return true
      }
    }
    return false
  }
}
