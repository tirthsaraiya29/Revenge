package com.revenge.usbaudio.model

/** How playback volume is applied for a USB DAC. */
enum class VolumeMode {
  /** Bits are forwarded untouched; volume is the external/hardware responsibility. */
  PURE_BIT_PERFECT,

  /** Attenuation is delegated to the DAC's UAC Feature Unit. */
  HARDWARE,

  /** High-precision software attenuation before the samples reach the DAC. */
  SOFTWARE_64BIT,
}
