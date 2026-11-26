package com.revenge.dsp.core.parser

import com.revenge.dsp.core.models.FilterType
import com.revenge.dsp.core.models.ParametricEQ
import com.revenge.dsp.core.models.ParametricEQBand
import java.io.File
import java.util.Locale

/**
 * Reads and writes AutoEQ-style parametric EQ text files.
 *
 * Input is tolerant: blank lines, unknown metadata and malformed or disabled
 * filter lines are skipped rather than failing the whole import. Call [validate]
 * afterwards to surface profiles that parsed but are outside usable ranges.
 */
object ParametricEQParser {

  private val preampPattern =
    Regex("""\bPreamp:\s*([-+]?\d+\.?\d*)\s*dB""", RegexOption.IGNORE_CASE)
  private val frequencyPattern =
    Regex("""\bFc\s+([-+]?\d+\.?\d*)\s*Hz""", RegexOption.IGNORE_CASE)
  private val gainPattern = Regex("""\bGain\s+([-+]?\d+\.?\d*)\s*dB""", RegexOption.IGNORE_CASE)
  private val qPattern = Regex("""\bQ\s+([-+]?\d+\.?\d*)""", RegexOption.IGNORE_CASE)

  fun parseFile(file: File): ParametricEQ {
    require(file.exists()) { "File does not exist: ${file.absolutePath}" }
    return parseText(file.readText())
  }

  fun parseFile(filePath: String): ParametricEQ = parseFile(File(filePath))

  fun parseText(content: String): ParametricEQ {
    var preamp = 0.0
    val bands = mutableListOf<ParametricEQBand>()
    val metadata = mutableMapOf<String, String>()

    for (rawLine in content.lineSequence()) {
      val line = rawLine.trim()
      when {
        line.isEmpty() -> Unit
        line.startsWith("Preamp:", ignoreCase = true) -> preamp = parsePreamp(line)
        line.startsWith("Filter", ignoreCase = true) -> parseFilterLine(line)?.let(bands::add)
        else -> {
          val parts = line.split(':', limit = 2)
          if (parts.size == 2) {
            metadata[parts[0].trim()] = parts[1].trim()
          }
        }
      }
    }

    return ParametricEQ(preamp = preamp, bands = bands, metadata = metadata)
  }

  fun toString(eq: ParametricEQ): String = buildString {
    appendLine("Preamp: ${eq.preamp} dB")
    eq.bands.forEachIndexed { index, band ->
      appendLine(
        "Filter ${index + 1}: ${band.filterType} Fc ${band.frequency} Hz " +
          "Gain ${band.gain} dB Q ${band.q}",
      )
    }
  }

  fun toFileFormat(eq: ParametricEQ): String = buildString {
    appendLine("Preamp: ${eq.preamp} dB")
    eq.bands.forEachIndexed { index, band ->
      appendLine(
        "Filter ${index + 1}: ON ${band.filterType} " +
          "Fc ${band.frequency.toInt()} Hz " +
          "Gain ${band.gain} dB " +
          "Q ${String.format(Locale.ROOT, "%.2f", band.q)}",
      )
    }
  }

  fun validate(eq: ParametricEQ): List<String> {
    val errors = mutableListOf<String>()

    if (eq.preamp < -50.0 || eq.preamp > 50.0) {
      errors.add("Preamp value ${eq.preamp} dB is out of range (-50 to +50 dB)")
    }

    if (eq.bands.isEmpty()) {
      errors.add("EQ profile must have at least one band")
    }

    if (eq.bands.size > ParametricEQ.MAX_BANDS) {
      errors.add("EQ profile has ${eq.bands.size} bands, maximum is ${ParametricEQ.MAX_BANDS}")
    }

    eq.bands.forEachIndexed { index, band ->
      if (band.frequency <= 0.0 || band.frequency > 100000.0) {
        errors.add("Band ${index + 1}: Frequency ${band.frequency} Hz is out of range (1 to 100000 Hz)")
      }
      if (band.gain < -30.0 || band.gain > 30.0) {
        errors.add("Band ${index + 1}: Gain ${band.gain} dB is out of range (-30 to +30 dB)")
      }
      if (band.q <= 0.0 || band.q > 20.0) {
        errors.add("Band ${index + 1}: Q factor ${band.q} is out of range (0.01 to 20)")
      }
    }

    return errors
  }

  private fun parsePreamp(line: String): Double =
    preampPattern.find(line)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

  private fun parseFilterLine(line: String): ParametricEQBand? {
    if (!line.contains("ON", ignoreCase = true)) return null

    val filterType = parseFilterType(line) ?: return null
    val frequency =
      frequencyPattern.find(line)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
    val gain = gainPattern.find(line)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
    val q = qPattern.find(line)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null

    return ParametricEQBand(
      frequency = frequency,
      gain = gain,
      q = q,
      filterType = filterType,
    )
  }

  private fun parseFilterType(line: String): FilterType? =
    when {
      line.contains("LSC", ignoreCase = true) -> FilterType.LSC
      line.contains("HSC", ignoreCase = true) -> FilterType.HSC
      line.contains("PK", ignoreCase = true) -> FilterType.PK
      line.contains("LPQ", ignoreCase = true) -> FilterType.LPQ
      line.contains("HPQ", ignoreCase = true) -> FilterType.HPQ
      else -> null
    }
}
