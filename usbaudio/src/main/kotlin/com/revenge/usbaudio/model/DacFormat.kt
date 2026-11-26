package com.revenge.usbaudio.model

/**
 * An audio streaming format advertised by one alternate setting of a USB DAC endpoint.
 *
 * [bitDepth] is the meaningful resolution in bits, while [subslotBytes] is the container size used
 * on the wire (for example 24 bits of audio inside a 4-byte subslot). [syncEndpointAddress] is the
 * feedback endpoint used for asynchronous sinks, or `null` when the device is synchronous/adaptive.
 */
data class DacFormat(
  val interfaceNumber: Int,
  val altSetting: Int,
  val endpointAddress: Int,
  val syncEndpointAddress: Int? = null,
  val bitDepth: Int,
  val subslotBytes: Int,
  val channels: Int,
  val sampleRates: List<Int>,
)
