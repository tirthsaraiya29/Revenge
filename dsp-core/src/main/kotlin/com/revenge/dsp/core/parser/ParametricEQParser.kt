package com.revenge.dsp.core.parser

import com.revenge.dsp.core.models.FilterType
import com.revenge.dsp.core.models.ParametricEQ
import com.revenge.dsp.core.models.ParametricEQBand
import java.io.File
import java.util.Locale

object ParametricEQParser {

  private val PREAMP_PATTERN = Regex("""([-+]?\d+(?:\.\d+)?)\s*dB""", RegexOption.IGNORE_CASE)
  private val TYPE_PATTERN = Regex("""\b(LSC|HSC|LPQ|HPQ|PK)\b""", RegexOption.IGNORE_CASE)
  private val ENABLED_PATTERN = Regex("""\bON\b""", RegexOption.IGNORE_CASE)
  private val FREQUENCY_PATTERN =
    Regex("""\bFc\s+([-+]?\d+(?:\.\d+)?)\s*Hz""", RegexOption.IGNORE_CASE)
  private val GAIN_PATTERN =
    Regex("""\bGain\s+([-+]?\d+(?:\.\d+)?)\s*dB""", RegexOption.IGNORE_CASE)
  private val Q_PATTERN = Regex("""\bQ\s+([-+]?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)

  fun parseFile(file: File): ParametricEQ {
    require(file.exists()) { "EQ profile file not found: ${file.absolutePath}" }
    return parseText(file.readText())
  }

  fun parseFile(filePath: String): ParametricEQ = parseFile(File(filePath))

  fun parseText(content: String): ParametricEQ {
    var preamp = 0.0
    val bands = ArrayList<ParametricEQBand>()
    val metadata = LinkedHashMap<String, String>()

    for (rawLine in content.lineSequence()) {
      val line = rawLine.trim()
      if (line.isEmpty()) continue
      when {
        line.startsWith(PREAMP_DIRECTIVE, ignoreCase = true) -> {
          preamp = PREAMP_PATTERN.find(line)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        }
        line.startsWith(FILTER_DIRECTIVE, ignoreCase = true) -> {
          parseBand(line)?.let(bands::add)
        }
        line.contains(':') -> {
          val separator = line.indexOf(':')
          metadata[line.substring(0, separator).trim()] = line.substring(separator + 1).trim()
        }
      }
    }
    return ParametricEQ(preamp = preamp, bands = bands, metadata = metadata)
  }

  fun toString(eq: ParametricEQ): String = buildString {
    append("Preamp: ${eq.preamp} dB")
    eq.bands.forEachIndexed { index, band ->
      append('\n')
      append("Filter ${index + 1}: ${band.filterType}")
      append(" Fc ${band.frequency} Hz")
      append(" Gain ${band.gain} dB")
      append(" Q ${band.q}")
    }
  }

  fun toFileFormat(eq: ParametricEQ): String = buildString {
    append("Preamp: ${eq.preamp} dB")
    eq.bands.forEachIndexed { index, band ->
      append('\n')
      append("Filter ${index + 1}: ON ${band.filterType}")
      append(" Fc ${band.frequency.toInt()} Hz")
      append(" Gain ${band.gain} dB")
      append(" Q ${String.format(Locale.ROOT, "%.2f", band.q)}")
    }
  }

  fun validate(eq: ParametricEQ): List<String> {
    val problems = ArrayList<String>()
    if (eq.preamp < PREAMP_MIN || eq.preamp > PREAMP_MAX) {
      problems.add("Preamp must be between $PREAMP_MIN and $PREAMP_MAX dB")
    }
    if (eq.bands.isEmpty()) {
      problems.add("EQ profile must have at least one band")
    }
    if (eq.bands.size > ParametricEQ.MAX_BANDS) {
      problems.add("Too many bands: maximum is ${ParametricEQ.MAX_BANDS}")
    }
    eq.bands.forEachIndexed { index, band ->
      val position = index + 1
      if (band.frequency <= 0.0 || band.frequency > MAX_FREQUENCY) {
        problems.add(
          "Band $position: Frequency must be greater than 0 and at most $MAX_FREQUENCY Hz",
        )
      }
      if (band.gain < MIN_GAIN || band.gain > MAX_GAIN) {
        problems.add("Band $position: Gain must be between $MIN_GAIN and $MAX_GAIN dB")
      }
      if (band.q <= 0.0 || band.q > MAX_Q) {
        problems.add("Band $position: Q factor must be greater than 0 and at most $MAX_Q")
      }
    }
    return problems
  }

  private fun parseBand(line: String): ParametricEQBand? {
    if (!ENABLED_PATTERN.containsMatchIn(line)) return null
    val typeMatch = TYPE_PATTERN.find(line) ?: return null
    val frequency =
      FREQUENCY_PATTERN.find(line)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
    val gain = GAIN_PATTERN.find(line)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
    val q = Q_PATTERN.find(line)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
    val filterType =
      FilterType.entries.firstOrNull { it.name.equals(typeMatch.value, ignoreCase = true) }
        ?: return null
    return ParametricEQBand(
      frequency = frequency,
      gain = gain,
      q = q,
      filterType = filterType,
      enabled = true,
    )
  }

  private const val PREAMP_DIRECTIVE = "Preamp:"
  private const val FILTER_DIRECTIVE = "Filter"
  private const val PREAMP_MIN = -50.0
  private const val PREAMP_MAX = 50.0
  private const val MAX_FREQUENCY = 100000.0
  private const val MIN_GAIN = -30.0
  private const val MAX_GAIN = 30.0
  private const val MAX_Q = 20.0
}
