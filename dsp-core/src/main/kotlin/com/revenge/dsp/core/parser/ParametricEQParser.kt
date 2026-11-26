package com.revenge.dsp.core.parser

import com.revenge.dsp.core.models.FilterType
import com.revenge.dsp.core.models.ParametricEQ
import com.revenge.dsp.core.models.ParametricEQBand
import java.io.File
import java.util.Locale

object ParametricEQParser {

    private val preampPattern =
        Regex("""\bPreamp:\s*([-+]?\d+\.?\d*)\s*dB""", RegexOption.IGNORE_CASE)
    private val frequencyPattern =
        Regex("""\bFc\s+([-+]?\d+\.?\d*)\s*Hz""", RegexOption.IGNORE_CASE)
    private val gainPattern =
        Regex("""\bGain\s+([-+]?\d+\.?\d*)\s*dB""", RegexOption.IGNORE_CASE)
    private val qPattern =
        Regex("""\bQ\s+([-+]?\d+\.?\d*)""", RegexOption.IGNORE_CASE)

    private val typeTokens = listOf(
        FilterType.LSC to "LSC",
        FilterType.HSC to "HSC",
        FilterType.PK to "PK",
        FilterType.LPQ to "LPQ",
        FilterType.HPQ to "HPQ"
    )

    fun parseFile(file: File): ParametricEQ {
        require(file.exists()) { "File does not exist: ${file.absolutePath}" }
        return parseText(file.readText())
    }

    fun parseFile(filePath: String): ParametricEQ = parseFile(File(filePath))

    fun parseText(content: String): ParametricEQ {
        var preamp = 0.0
        val bands = mutableListOf<ParametricEQBand>()
        val metadata = linkedMapOf<String, String>()

        for (rawLine in content.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            when {
                line.startsWith("Preamp:", ignoreCase = true) -> {
                    preamp = preampPattern.find(line)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toDoubleOrNull()
                        ?: 0.0
                }

                line.startsWith("Filter", ignoreCase = true) -> {
                    parseBand(line)?.let { bands.add(it) }
                }

                else -> {
                    val separator = line.indexOf(':')
                    if (separator >= 0) {
                        val key = line.substring(0, separator).trim()
                        val value = line.substring(separator + 1).trim()
                        metadata[key] = value
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
                "Filter ${index + 1}: ${band.filterType} " +
                    "Fc ${band.frequency} Hz Gain ${band.gain} dB Q ${band.q}"
            )
        }
    }

    fun toFileFormat(eq: ParametricEQ): String = buildString {
        appendLine("Preamp: ${eq.preamp} dB")
        eq.bands.forEachIndexed { index, band ->
            val q = String.format(Locale.ROOT, "%.2f", band.q)
            appendLine(
                "Filter ${index + 1}: ON ${band.filterType} " +
                    "Fc ${band.frequency.toInt()} Hz Gain ${band.gain} dB Q $q"
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
            val number = index + 1
            if (band.frequency <= 0.0 || band.frequency > 100000.0) {
                errors.add(
                    "Band $number: Frequency ${band.frequency} Hz is out of range (1 to 100000 Hz)"
                )
            }
            if (band.gain < -30.0 || band.gain > 30.0) {
                errors.add("Band $number: Gain ${band.gain} dB is out of range (-30 to +30 dB)")
            }
            if (band.q <= 0.0 || band.q > 20.0) {
                errors.add("Band $number: Q factor ${band.q} is out of range (0.01 to 20)")
            }
        }

        return errors
    }

    private fun parseBand(line: String): ParametricEQBand? {
        if (!line.contains("ON", ignoreCase = true)) return null

        val upper = line.uppercase(Locale.ROOT)
        val filterType = typeTokens.firstOrNull { upper.contains(it.second) }?.first ?: return null

        val frequency = frequencyPattern.find(line)
            ?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: return null
        val gain = gainPattern.find(line)
            ?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: return null
        val q = qPattern.find(line)
            ?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: return null

        return ParametricEQBand(
            frequency = frequency,
            gain = gain,
            q = q,
            filterType = filterType,
            enabled = true
        )
    }
}
