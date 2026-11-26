package com.revenge.usbaudio

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import com.revenge.usbaudio.model.DacCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Lifecycle state of the currently selected USB DAC. */
sealed class DacDeviceState {
  object Disconnected : DacDeviceState()

  data class PermissionRequired(val device: UsbDevice) : DacDeviceState()

  data class Connected(
    val device: UsbDevice,
    val connection: UsbDeviceConnection,
    val capabilities: DacCapabilities,
    val fileDescriptor: Int,
  ) : DacDeviceState()

  data class Error(val message: String) : DacDeviceState()
}

/**
 * Owns USB host discovery and permission negotiation for a DAC.
 *
 * Call [register] when playback starts and [unregister] when it stops. Attachment, detachment and
 * permission grants are delivered through a broadcast receiver and published on [activeDacFlow].
 */
class UsbDacManager(
  private val context: Context,
  private val driver: UsbAudioDriver = UsbAudioDriver(),
  private val detector: UsbDeviceDetector = UsbDeviceDetector(),
) {
  companion object {
    const val ACTION_USB_PERMISSION = "com.revenge.usbaudio.USB_PERMISSION"
  }

  private val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager

  private val _activeDacFlow = MutableStateFlow<DacDeviceState>(DacDeviceState.Disconnected)
  val activeDacFlow: StateFlow<DacDeviceState> = _activeDacFlow.asStateFlow()

  /** Invoked after the active DAC is detached and disconnected. */
  var onDeviceDetachedCallback: (() -> Unit)? = null

  private var receiverRegistered = false

  private val usbReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(receiverContext: Context?, intent: Intent?) {
        when (intent?.action) {
          UsbManager.ACTION_USB_DEVICE_ATTACHED ->
            intent.deviceExtra()?.let(::onDeviceAttached)

          UsbManager.ACTION_USB_DEVICE_DETACHED ->
            intent.deviceExtra()?.let(::onDeviceDetached)

          ACTION_USB_PERMISSION -> {
            val device = intent.deviceExtra()
            val granted =
              intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            if (device != null && granted) {
              connect(device)
            } else {
              _activeDacFlow.value = DacDeviceState.Error("USB permission denied")
            }
          }
        }
      }
    }

  /** Starts listening for USB events and adopts an already-attached DAC, if any. */
  fun register() {
    if (receiverRegistered) {
      scanExistingDevices()
      return
    }
    val filter =
      IntentFilter().apply {
        addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        addAction(ACTION_USB_PERMISSION)
      }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
      context.registerReceiver(usbReceiver, filter)
    }
    receiverRegistered = true
    scanExistingDevices()
  }

  /** Stops listening and closes the active connection. */
  fun unregister() {
    if (receiverRegistered) {
      try {
        context.unregisterReceiver(usbReceiver)
      } catch (_: IllegalArgumentException) {
        // Already unregistered by the framework.
      }
      receiverRegistered = false
    }
    disconnect()
  }

  /** Adopts the first attached USB audio device that is not already handled. */
  fun scanExistingDevices() {
    val manager = usbManager ?: return
    for (device in manager.deviceList.values) {
      if (detector.isUsbAudioDevice(device)) {
        onDeviceAttached(device)
        return
      }
    }
  }

  /** Requests the standard USB permission dialog for [device]. */
  fun requestPermission(device: UsbDevice) {
    val manager = usbManager ?: return
    val flags =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
      } else {
        PendingIntent.FLAG_UPDATE_CURRENT
      }
    val permissionIntent =
      PendingIntent.getBroadcast(
        context,
        0,
        Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
        flags,
      )
    manager.requestPermission(device, permissionIntent)
  }

  private fun onDeviceAttached(device: UsbDevice) {
    if (!detector.isUsbAudioDevice(device)) return
    val manager = usbManager ?: return
    if (manager.hasPermission(device)) {
      connect(device)
    } else {
      _activeDacFlow.value = DacDeviceState.PermissionRequired(device)
      requestPermission(device)
    }
  }

  private fun onDeviceDetached(device: UsbDevice) {
    val current = _activeDacFlow.value
    if (current is DacDeviceState.Connected && current.device.deviceId == device.deviceId) {
      disconnect()
      onDeviceDetachedCallback?.invoke()
    }
  }

  private fun connect(device: UsbDevice) {
    val current = _activeDacFlow.value
    if (current is DacDeviceState.Connected) {
      if (current.device.deviceId == device.deviceId) return
      disconnect()
    }

    val manager = usbManager ?: return
    val connection = manager.openDevice(device)
    if (connection == null) {
      _activeDacFlow.value = DacDeviceState.Error("Failed to open USB device connection")
      return
    }

    val descriptors = connection.rawDescriptors ?: ByteArray(0)
    _activeDacFlow.value =
      DacDeviceState.Connected(
        device = device,
        connection = connection,
        capabilities = driver.parseDescriptors(descriptors),
        fileDescriptor = connection.fileDescriptor,
      )
  }

  private fun disconnect() {
    val current = _activeDacFlow.value
    if (current is DacDeviceState.Connected) {
      try {
        driver.stopStream()
      } catch (_: Exception) {
        // The transport is best-effort during teardown.
      }
      try {
        current.connection.close()
      } catch (_: Exception) {
        // The device may already be gone.
      }
    }
    _activeDacFlow.value = DacDeviceState.Disconnected
  }

  @Suppress("DEPRECATION")
  private fun Intent.deviceExtra(): UsbDevice? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
    } else {
      getParcelableExtra(UsbManager.EXTRA_DEVICE)
    }
}
