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

class UsbDacManager(
  private val context: Context,
  private val driver: UsbAudioDriver = UsbAudioDriver(),
  private val detector: UsbDeviceDetector = UsbDeviceDetector(),
) {
  companion object {
    const val ACTION_USB_PERMISSION = "com.revenge.usbaudio.USB_PERMISSION"
  }

  private val state = MutableStateFlow<DacDeviceState>(DacDeviceState.Disconnected)
  val activeDacFlow: StateFlow<DacDeviceState> = state.asStateFlow()

  var onDeviceDetachedCallback: (() -> Unit)? = null

  private var registered = false

  private val receiver =
    object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
          UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
            val device = deviceFromIntent(intent) ?: return
            onAttached(device)
          }
          UsbManager.ACTION_USB_DEVICE_DETACHED -> {
            val device = deviceFromIntent(intent) ?: return
            onDetached(device)
          }
          ACTION_USB_PERMISSION -> {
            val device = deviceFromIntent(intent) ?: return
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            onPermissionResult(device, granted)
          }
        }
      }
    }

  @Synchronized
  fun register() {
    if (registered) {
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
      context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
      context.registerReceiver(receiver, filter)
    }
    registered = true
    scanExistingDevices()
  }

  @Synchronized
  fun unregister() {
    if (registered) {
      try {
        context.unregisterReceiver(receiver)
      } catch (error: IllegalArgumentException) {
        // Receiver was already unregistered by the framework.
      }
      registered = false
    }
    disconnect()
  }

  fun scanExistingDevices() {
    val manager = usbManager() ?: return
    for (device in manager.deviceList.values) {
      if (!detector.isUsbAudioDevice(device)) continue
      adopt(manager, device)
      return
    }
  }

  fun requestPermission(device: UsbDevice) {
    val manager = usbManager() ?: return
    val flags =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
      } else {
        PendingIntent.FLAG_UPDATE_CURRENT
      }
    val intent = Intent(ACTION_USB_PERMISSION).apply { setPackage(context.packageName) }
    val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, flags)
    manager.requestPermission(device, pendingIntent)
  }

  private fun onAttached(device: UsbDevice) {
    if (!detector.isUsbAudioDevice(device)) return
    val manager = usbManager() ?: return
    adopt(manager, device)
  }

  private fun onPermissionResult(device: UsbDevice, granted: Boolean) {
    val manager = usbManager()
    if (granted && manager != null && isPresent(manager, device)) {
      connect(device)
    } else {
      state.value = DacDeviceState.Error("USB permission denied")
    }
  }

  private fun onDetached(device: UsbDevice) {
    val current = state.value
    if (current is DacDeviceState.Connected && current.device.deviceId == device.deviceId) {
      disconnect()
      onDeviceDetachedCallback?.invoke()
    }
  }

  private fun adopt(manager: UsbManager, device: UsbDevice) {
    if (manager.hasPermission(device)) {
      connect(device)
    } else {
      state.value = DacDeviceState.PermissionRequired(device)
      requestPermission(device)
    }
  }

  private fun connect(device: UsbDevice) {
    val manager = usbManager() ?: return
    val current = state.value
    if (current is DacDeviceState.Connected) {
      if (current.device.deviceId == device.deviceId) return
      disconnect()
    }
    val connection = manager.openDevice(device)
    if (connection == null) {
      state.value = DacDeviceState.Error("Failed to open USB device connection")
      return
    }
    val descriptors = connection.rawDescriptors ?: ByteArray(0)
    val capabilities = driver.parseDescriptors(descriptors)
    state.value =
      DacDeviceState.Connected(
        device = device,
        connection = connection,
        capabilities = capabilities,
        fileDescriptor = connection.fileDescriptor,
      )
  }

  private fun disconnect() {
    val current = state.value
    if (current is DacDeviceState.Connected) {
      try {
        driver.stopStream()
      } catch (error: Exception) {
        // The device may already have gone away.
      }
      try {
        current.connection.close()
      } catch (error: Exception) {
        // The device may already have gone away.
      }
      state.value = DacDeviceState.Disconnected
    }
  }

  private fun isPresent(manager: UsbManager, device: UsbDevice): Boolean =
    manager.deviceList.values.any { it.deviceId == device.deviceId }

  private fun usbManager(): UsbManager? =
    context.getSystemService(Context.USB_SERVICE) as? UsbManager
}

@Suppress("DEPRECATION")
private fun deviceFromIntent(intent: Intent): UsbDevice? =
  intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
