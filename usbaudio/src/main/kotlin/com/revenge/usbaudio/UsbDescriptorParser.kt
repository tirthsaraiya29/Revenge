package com.revenge.usbaudio

import com.revenge.usbaudio.model.DacCapabilities
import com.revenge.usbaudio.model.DacFormat

/**
 * Parses a raw USB configuration descriptor blob into [DacCapabilities].
 *
 * The parser walks every descriptor, tracking the current interface so class-specific Audio
 * Control and Audio Streaming descriptors can be attributed correctly. It understands UAC 1
 * Format Type I descriptors (discrete and continuous sample-rate ranges) and UAC 2 clock sources.
 * The input is untrusted: every length is validated and malformed data stops the walk instead of
 * reading out of bounds.
 */
internal object UsbDescriptorParser {

  private const val DT_INTERFACE = 0x04
  private const val DT_ENDPOINT = 0x05
  private const val DT_CS_INTERFACE = 0x24

  private const val CLASS_AUDIO = 0x01
  private const val SUBCLASS_AUDIOCONTROL = 0x01
  private const val SUBCLASS_AUDIOSTREAMING = 0x02

  private const val PROTOCOL_UAC1 = 0x00
  private const val PROTOCOL_UAC2 = 0x20

  private const val SUBTYPE_AS_GENERAL = 0x01
  private const val SUBTYPE_FORMAT_TYPE = 0x02
  private const val SUBTYPE_FEATURE_UNIT = 0x06
  private const val SUBTYPE_CLOCK_SOURCE = 0x0A
  private const val FORMAT_TYPE_I = 0x01

  private const val ATTRIBUTE_TRANSFER_TYPE_MASK = 0x03
  private const val ATTRIBUTE_ISOCHRONOUS = 0x01
  private const val ENDPOINT_DIRECTION_IN = 0x80

  private const val UAC2_DEFAULT_RATE = 48_000

  private val UAC1_CONTINUOUS_RATES =
    intArrayOf(44_100, 48_000, 88_200, 96_000, 176_400, 192_000)

  private class PendingFormat(val interfaceNumber: Int, val altSetting: Int) {
    var endpointAddress = 0
    var syncEndpointAddress: Int? = null
    var bitDepth = 16
    var subslotBytes = 2
    var channels = 2
    val sampleRates = LinkedHashSet<Int>()

    fun toFormat(): DacFormat =
      DacFormat(
        interfaceNumber = interfaceNumber,
        altSetting = altSetting,
        endpointAddress = endpointAddress,
        syncEndpointAddress = syncEndpointAddress,
        bitDepth = bitDepth,
        subslotBytes = subslotBytes,
        channels = channels,
        sampleRates = sampleRates.toList(),
      )
  }

  fun parse(data: ByteArray): DacCapabilities {
    if (data.size < 2) return DacCapabilities(uacVersion = 1)

    var uacVersion = 1
    var clockSourceId: Int? = null
    var hasHardwareVolume = false
    var volumeFeatureUnitId: Int? = null
    val formats = ArrayList<DacFormat>()
    val allRates = sortedSetOf<Int>()

    var interfaceClass = -1
    var interfaceSubclass = -1
    var pending: PendingFormat? = null

    fun finishPending() {
      val format = pending ?: return
      if (format.sampleRates.isNotEmpty() || uacVersion == 2) {
        formats.add(format.toFormat())
      }
      pending = null
    }

    var offset = 0
    while (offset + 2 <= data.size) {
      val length = data[offset].toInt() and 0xFF
      if (length < 2 || offset + length > data.size) break
      val type = data[offset + 1].toInt() and 0xFF

      when (type) {
        DT_INTERFACE -> {
          if (length >= 9) {
            val number = data[offset + 2].toInt() and 0xFF
            val altSetting = data[offset + 3].toInt() and 0xFF
            interfaceClass = data[offset + 5].toInt() and 0xFF
            interfaceSubclass = data[offset + 6].toInt() and 0xFF
            val protocol = data[offset + 7].toInt() and 0xFF

            if (
              interfaceClass == CLASS_AUDIO && interfaceSubclass == SUBCLASS_AUDIOCONTROL
            ) {
              when (protocol) {
                PROTOCOL_UAC2 -> uacVersion = 2
                PROTOCOL_UAC1 -> uacVersion = 1
              }
            }

            finishPending()

            if (
              interfaceClass == CLASS_AUDIO &&
                interfaceSubclass == SUBCLASS_AUDIOSTREAMING &&
                altSetting > 0
            ) {
              pending = PendingFormat(number, altSetting)
            }
          }
        }

        DT_CS_INTERFACE -> {
          if (length >= 3) {
            val subtype = data[offset + 2].toInt() and 0xFF
            if (interfaceClass == CLASS_AUDIO && interfaceSubclass == SUBCLASS_AUDIOCONTROL) {
              if (
                uacVersion == 2 && subtype == SUBTYPE_CLOCK_SOURCE && length >= 8
              ) {
                clockSourceId = data[offset + 3].toInt() and 0xFF
                // UAC 2 rates live behind a control request; assume 48 kHz until queried.
                allRates.add(UAC2_DEFAULT_RATE)
              } else if (subtype == SUBTYPE_FEATURE_UNIT && length >= 6) {
                hasHardwareVolume = true
                volumeFeatureUnitId = data[offset + 3].toInt() and 0xFF
              }
            } else if (
              interfaceClass == CLASS_AUDIO && interfaceSubclass == SUBCLASS_AUDIOSTREAMING
            ) {
              val format = pending
              if (format != null) {
                readStreamingDescriptor(data, offset, length, subtype, uacVersion, format, allRates)
              }
            }
          }
        }

        DT_ENDPOINT -> {
          val format = pending
          if (format != null && length >= 7) {
            val attributes = data[offset + 3].toInt() and ATTRIBUTE_TRANSFER_TYPE_MASK
            if (attributes == ATTRIBUTE_ISOCHRONOUS) {
              val address = data[offset + 2].toInt() and 0xFF
              if (address and ENDPOINT_DIRECTION_IN == 0) {
                format.endpointAddress = address
              } else {
                format.syncEndpointAddress = address
              }
            }
          }
        }
      }

      offset += length
    }

    finishPending()

    if (uacVersion == 2) {
      val inherited = allRates.toList()
      for (index in formats.indices) {
        val format = formats[index]
        if (format.sampleRates.isEmpty()) {
          formats[index] = format.copy(sampleRates = inherited)
        }
      }
    }

    return DacCapabilities(
      uacVersion = uacVersion,
      supportedFormats = formats,
      supportedSampleRates = allRates.toList(),
      clockSourceId = clockSourceId,
      hasHardwareVolume = hasHardwareVolume,
      volumeFeatureUnitId = volumeFeatureUnitId,
    )
  }

  private fun readStreamingDescriptor(
    data: ByteArray,
    offset: Int,
    length: Int,
    subtype: Int,
    uacVersion: Int,
    format: PendingFormat,
    allRates: MutableSet<Int>,
  ) {
    when {
      subtype == SUBTYPE_AS_GENERAL -> {
        // UAC 2 AS General: bNrChannels sits at byte 10.
        if (uacVersion == 2 && length >= 11) {
          format.channels = data[offset + 10].toInt() and 0xFF
        }
      }

      subtype == SUBTYPE_FORMAT_TYPE && length >= 4 -> {
        val formatType = data[offset + 3].toInt() and 0xFF
        if (formatType != FORMAT_TYPE_I) return

        if (uacVersion == 2) {
          if (length >= 7) {
            format.subslotBytes = data[offset + 4].toInt() and 0xFF
            format.bitDepth = data[offset + 5].toInt() and 0xFF
          }
        } else if (length >= 8) {
          format.channels = data[offset + 4].toInt() and 0xFF
          format.subslotBytes = data[offset + 5].toInt() and 0xFF
          format.bitDepth = data[offset + 6].toInt() and 0xFF
          val rateCount = data[offset + 7].toInt() and 0xFF

          if (rateCount > 0) {
            val available = (length - 8) / 3
            repeat(minOf(rateCount, available)) { index ->
              val rateOffset = offset + 8 + index * 3
              val rate = readUint24(data, rateOffset)
              if (rate > 0) {
                format.sampleRates.add(rate)
                allRates.add(rate)
              }
            }
          } else if (length >= 14) {
            readContinuousRange(data, offset + 8, format, allRates)
          }
        }
      }
    }
  }

  private fun readContinuousRange(
    data: ByteArray,
    rangeOffset: Int,
    format: PendingFormat,
    allRates: MutableSet<Int>,
  ) {
    val minRate = readUint24(data, rangeOffset)
    val maxRate = readUint24(data, rangeOffset + 3)
    if (maxRate < minRate) return

    val matching = UAC1_CONTINUOUS_RATES.filter { it in minRate..maxRate }
    if (matching.isNotEmpty()) {
      for (rate in matching) {
        format.sampleRates.add(rate)
        allRates.add(rate)
      }
      return
    }

    if (minRate > 0) {
      format.sampleRates.add(minRate)
      allRates.add(minRate)
      if (maxRate != minRate) {
        format.sampleRates.add(maxRate)
        allRates.add(maxRate)
      }
    }
  }

  private fun readUint24(data: ByteArray, offset: Int): Int =
    (data[offset].toInt() and 0xFF) or
      ((data[offset + 1].toInt() and 0xFF) shl 8) or
      ((data[offset + 2].toInt() and 0xFF) shl 16)
}
