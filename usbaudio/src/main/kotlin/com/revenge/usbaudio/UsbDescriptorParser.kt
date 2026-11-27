package com.revenge.usbaudio

import com.revenge.usbaudio.model.DacCapabilities
import com.revenge.usbaudio.model.DacFormat

/**
 * Best-effort reader for a raw USB configuration descriptor blob. Every read is bounded by the
 * record length, and malformed input yields whatever was decoded up to that point.
 */
internal object UsbDescriptorParser {

  private const val TYPE_INTERFACE = 0x04
  private const val TYPE_ENDPOINT = 0x05
  private const val TYPE_CS_INTERFACE = 0x24

  private const val CLASS_AUDIO = 0x01
  private const val SUBCLASS_AUDIO_CONTROL = 0x01
  private const val SUBCLASS_AUDIO_STREAMING = 0x02
  private const val PROTOCOL_UAC2 = 0x20

  private const val CS_AS_GENERAL = 0x01
  private const val CS_FORMAT_TYPE = 0x02
  private const val CS_FEATURE_UNIT = 0x06
  private const val CS_CLOCK_SOURCE = 0x0A

  private const val FORMAT_TYPE_I = 0x01
  private const val ISOCHRONOUS_TRANSFER = 0x01
  private const val ENDPOINT_IN_BIT = 0x80

  private val UAC1_STANDARD_RATES = intArrayOf(44100, 48000, 88200, 96000, 176400, 192000)

  private class OpenFormat(val interfaceNumber: Int, val altSetting: Int) {
    var endpointAddress: Int = 0
    var syncEndpointAddress: Int? = null
    var bitDepth: Int = 0
    var subslotBytes: Int = 0
    var channels: Int = 0
    val sampleRates: MutableList<Int> = mutableListOf()
  }

  fun parse(data: ByteArray): DacCapabilities {
    var uacVersion = 1
    var clockSourceId: Int? = null
    var hasHardwareVolume = false
    var volumeFeatureUnitId: Int? = null
    val formats = mutableListOf<DacFormat>()
    val clockRates = linkedSetOf<Int>()
    var currentSubclass = 0
    var open: OpenFormat? = null

    fun closeOpen() {
      val format = open ?: return
      open = null
      if (format.sampleRates.isEmpty() && uacVersion != 2) return
      formats.add(
        DacFormat(
          interfaceNumber = format.interfaceNumber,
          altSetting = format.altSetting,
          endpointAddress = format.endpointAddress,
          syncEndpointAddress = format.syncEndpointAddress,
          bitDepth = format.bitDepth,
          subslotBytes = format.subslotBytes,
          channels = format.channels,
          sampleRates = format.sampleRates.toList(),
        ),
      )
    }

    var cursor = 0
    while (cursor + 2 <= data.size) {
      val length = data[cursor].toInt() and 0xFF
      val descriptorType = data[cursor + 1].toInt() and 0xFF
      if (length < 2) break
      if (cursor + length > data.size) break
      val recordEnd = cursor + length

      when (descriptorType) {
        TYPE_INTERFACE -> {
          if (length >= 9) {
            val interfaceNumber = data[cursor + 2].toInt() and 0xFF
            val altSetting = data[cursor + 3].toInt() and 0xFF
            val interfaceClass = data[cursor + 5].toInt() and 0xFF
            val interfaceSubclass = data[cursor + 6].toInt() and 0xFF
            val interfaceProtocol = data[cursor + 7].toInt() and 0xFF

            currentSubclass = if (interfaceClass == CLASS_AUDIO) interfaceSubclass else 0
            if (interfaceClass == CLASS_AUDIO && interfaceSubclass == SUBCLASS_AUDIO_CONTROL) {
              uacVersion = if (interfaceProtocol == PROTOCOL_UAC2) 2 else 1
            }
            closeOpen()
            if (
              interfaceClass == CLASS_AUDIO &&
                interfaceSubclass == SUBCLASS_AUDIO_STREAMING &&
                altSetting > 0
            ) {
              open = OpenFormat(interfaceNumber, altSetting)
            }
          } else {
            closeOpen()
            currentSubclass = 0
          }
        }

        TYPE_CS_INTERFACE -> {
          if (length >= 3) {
            val subtype = data[cursor + 2].toInt() and 0xFF
            readCsInterface(
              data = data,
              cursor = cursor,
              length = length,
              subtype = subtype,
              subclass = currentSubclass,
              uacVersion = uacVersion,
              open = open,
              onClockSource = { identifier ->
                clockSourceId = identifier
                clockRates.add(UAC2_SEED_RATE)
              },
              onFeatureUnit = { identifier ->
                hasHardwareVolume = true
                volumeFeatureUnitId = identifier
              },
            )
          }
        }

        TYPE_ENDPOINT -> {
          if (length >= 7) {
            val format = open
            if (format != null) {
              val transferType = data[cursor + 3].toInt() and 0x03
              if (transferType == ISOCHRONOUS_TRANSFER) {
                val address = data[cursor + 2].toInt() and 0xFF
                if (address and ENDPOINT_IN_BIT != 0) {
                  format.syncEndpointAddress = address
                } else {
                  format.endpointAddress = address
                }
              }
            }
          }
        }
      }
      cursor = recordEnd
    }
    closeOpen()

    if (uacVersion == 2 && clockRates.isNotEmpty()) {
      for (index in formats.indices) {
        val format = formats[index]
        if (format.sampleRates.isEmpty()) {
          formats[index] = format.copy(sampleRates = clockRates.toList())
        }
      }
    }

    val union = formats.flatMap { it.sampleRates }.toSortedSet().toList()
    return DacCapabilities(
      uacVersion = uacVersion,
      supportedFormats = formats,
      supportedSampleRates = union,
      clockSourceId = clockSourceId,
      hasHardwareVolume = hasHardwareVolume,
      volumeFeatureUnitId = volumeFeatureUnitId,
    )
  }

  private fun readCsInterface(
    data: ByteArray,
    cursor: Int,
    length: Int,
    subtype: Int,
    subclass: Int,
    uacVersion: Int,
    open: OpenFormat?,
    onClockSource: (Int) -> Unit,
    onFeatureUnit: (Int) -> Unit,
  ) {
    when (subclass) {
      SUBCLASS_AUDIO_CONTROL -> {
        when {
          subtype == CS_CLOCK_SOURCE && uacVersion == 2 && length >= 8 -> {
            onClockSource(data[cursor + 3].toInt() and 0xFF)
          }
          subtype == CS_FEATURE_UNIT && length >= 6 -> {
            onFeatureUnit(data[cursor + 3].toInt() and 0xFF)
          }
        }
      }

      SUBCLASS_AUDIO_STREAMING -> {
        val format = open ?: return
        when (subtype) {
          CS_AS_GENERAL -> {
            if (uacVersion == 2 && length >= 11) {
              format.channels = data[cursor + 10].toInt() and 0xFF
            }
          }

          CS_FORMAT_TYPE -> {
            if (length >= 4 && (data[cursor + 3].toInt() and 0xFF) == FORMAT_TYPE_I) {
              if (uacVersion == 2) {
                if (length >= 7) {
                  format.subslotBytes = data[cursor + 4].toInt() and 0xFF
                  format.bitDepth = data[cursor + 5].toInt() and 0xFF
                }
              } else if (length >= 8) {
                format.channels = data[cursor + 4].toInt() and 0xFF
                format.subslotBytes = data[cursor + 5].toInt() and 0xFF
                format.bitDepth = data[cursor + 6].toInt() and 0xFF
                val rateCount = data[cursor + 7].toInt() and 0xFF
                if (rateCount > 0) {
                  val available = (length - 8) / 3
                  val read = minOf(rateCount, available)
                  for (index in 0 until read) {
                    val rate = readRate24(data, cursor + 8 + index * 3)
                    if (rate > 0) format.sampleRates.add(rate)
                  }
                } else if (length >= 14) {
                  val minimum = readRate24(data, cursor + 8)
                  val maximum = readRate24(data, cursor + 11)
                  if (maximum >= minimum) {
                    val matched = UAC1_STANDARD_RATES.filter { it in minimum..maximum }
                    if (matched.isNotEmpty()) {
                      format.sampleRates.addAll(matched)
                    } else {
                      format.sampleRates.add(minimum)
                      if (maximum != minimum && minimum > 0) {
                        format.sampleRates.add(maximum)
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  private fun readRate24(data: ByteArray, offset: Int): Int {
    if (offset + 2 >= data.size) return 0
    return (data[offset].toInt() and 0xFF) or
      ((data[offset + 1].toInt() and 0xFF) shl 8) or
      ((data[offset + 2].toInt() and 0xFF) shl 16)
  }

  private const val UAC2_SEED_RATE = 48000
}
