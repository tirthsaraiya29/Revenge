package com.revenge.dsp.core.parser

import com.revenge.dsp.core.models.FilterType
import com.revenge.dsp.core.models.ParametricEQ
import com.revenge.dsp.core.models.ParametricEQBand
import java.io.File
import java.util.Locale

object ParametricEQParser {

    private val PREAMP_REGEX = Regex("""(?i)Preamp:\s*([-+]?\d+\.?\d*)\s*dB""")
    private val FC_REGEX = Regex("""(?i)\bFc\s*([-+]?\d+\.?\d*)\s*Hz""")
    private val GAIN_REGEX = Regex("""(?i)\bGain\s*([-+]?\d+\.?\d*)\s*dB""")
    private val Q_REGEX = Regex("""(?i)(?:^|[^A-Za-z])Q\s*([-+]?\d+\.?\d*)""")
    private val ON_REGEX = Regex("""(?i)\bON\b""")

    fun parseFile(file: File): ParametricEQ {
        require(file.exists()) { "EQ file does not exist: ${file.absolutePath}" }
        return parseText(file.readText())
    }

    fun parseFile(filePath: String): ParametricEQ = parseFile(File(filePath))

    fun parseText(content: String): ParametricEQ {
        var preamp = 0.0
        val bands = mutableListOf<ParametricEQBand>()
        val metadata = linkedMapOf<String, String>()

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach

            val lower = line.lowercase()
            if (lower.startsWith("preamp:")) {
                preamp = PREAMP_REGEX.find(line)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                return@forEach
            }

            if (lower.startsWith("filter")) {
                if (!ON_REGEX.containsMatchIn(line)) return@forEach
                val type = detectFilterType(line) ?: return@forEach
                val frequency = FC_REGEX.find(line)?.groupValues?.get(1)?.toDoubleOrNull() ?: return@forEach
                val gain = GAIN_REGEX.find(line)?.groupValues?.get(1)?.toDoubleOrNull() ?: return@forEach
                val q = Q_REGEX.find(line)?.groupValues?.get(1)?.toDoubleOrNull() ?: return@forEach
                bands.add(
                    ParametricEQBand(
                        frequency = frequency,
                        gain = gain,
                        q = q,
                        filterType = type,
                        enabled = true,
                    )
                )
                return@forEach
            }

            val colon = line.indexOf(':')
            if (colon >= 0) {
                val key = line.substring(0, colon).trim()
                val value = line.substring(colon + 1).trim()
                if (key.isNotEmpty()) metadata[key] = value
            }
        }

        return ParametricEQ(preamp = preamp, bands = bands, metadata = metadata)
    }

    fun toString(eq: ParametricEQ): String = buildString {
        append("Preamp: ${eq.preamp} dB")
        eq.bands.forEachIndexed { index, band ->
            append("\nFilter ${index + 1}: ${band.filterType} Fc ${band.frequency} Hz")
            append(" Gain ${band.gain} dB Q ${band.q}")
        }
    }

    fun toFileFormat(eq: ParametricEQ): String = buildString {
        append("Preamp: ${eq.preamp} dB")
        eq.bands.forEachIndexed { index, band ->
            val q = String.format(Locale.ROOT, "%.2f", band.q)
            append("\nFilter ${index + 1}: ON ${band.filterType} Fc ${band.frequency.toInt()} Hz")
            append(" Gain ${band.gain} dB Q $q")
        }
    }

    fun validate(eq: ParametricEQ): List<String> {
        val errors = mutableListOf<String>()

        if (eq.preamp < -50.0 || eq.preamp > 50.0) {
            errors.add("Preamp must be between -50.0 and 50.0 dB")
        }

        if (eq.bands.isEmpty()) {
            errors.add("EQ profile must have at least one band")
            return errors
        }

        if (eq.bands.size > ParametricEQ.MAX_BANDS) {
            errors.add("Too many bands: ${eq.bands.size}; maximum is 20")
        }

        eq.bands.forEachIndexed { index, band ->
            val number = index + 1
            if (band.frequency <= 0.0 || band.frequency > 100000.0) {
                errors.add("Frequency of band $number must be between 0 and 100000 Hz")
            }
            if (band.gain < -30.0 || band.gain > 30.0) {
                errors.add("Gain of band $number must be between -30 and 30 dB")
            }
            if (band.q <= 0.0 || band.q > 20.0) {
                errors.add("Q factor of band $number must be between 0 and 20")
            }
        }

        return errors
    }

    private fun detectFilterType(line: String): FilterType? {
        val upper = line.uppercase(Locale.ROOT)
        return when {
            upper.contains("LSC") -> FilterType.LSC
            upper.contains("HSC") -> FilterType.HSC
            upper.contains("LPQ") -> FilterType.LPQ
            upper.contains("HPQ") -> FilterType.HPQ
            upper.contains("PK") -> FilterType.PK
            else -> null
        }
    }
}
