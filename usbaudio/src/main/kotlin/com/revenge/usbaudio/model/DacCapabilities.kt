package com.revenge.usbaudio.model

/**
 * Everything learned about a DAC from its USB configuration descriptors.
 *
 * [uacVersion] is 1 for USB Audio Class 1.0 devices and 2 for UAC 2.0 devices. A UAC 2 device
 * exposes its sample rates through the clock source entity; the parser cannot issue control
 * transfers while enumerating, so [supportedSampleRates] starts with the 48 kHz default the device
 * is expected to accept and can be refined by the caller after a GET_RANGE request.
 */
data class DacCapabilities(
  val uacVersion: Int,
  val supportedFormats: List<DacFormat> = emptyList(),
  val supportedSampleRates: List<Int> = emptyList(),
  val clockSourceId: Int? = null,
  val hasHardwareVolume: Boolean = false,
  val volumeFeatureUnitId: Int? = null,
  val minVolumeDb: Float = 0.0f,
  val maxVolumeDb: Float = 0.0f,
  val volumeResDb: Float = 0.0f,
)
