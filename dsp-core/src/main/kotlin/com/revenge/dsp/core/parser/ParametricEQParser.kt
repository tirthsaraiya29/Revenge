package com.revenge.dsp.core.parser

import com.revenge.dsp.core.models.FilterType
import com.revenge.dsp.core.models.ParametricEQ
import com.revenge.dsp.core.models.ParametricEQBand
import java.io.File
import java.nio.charset.Charset
import java.util.Locale

/**
 * Stateless reader/writer for AutoEQ-style parametric EQ text.
 *
 * Parsing is tolerant: classification never throws, and a line that cannot produce a usable
 * entity is discarded rather than partially applied.
 */
object ParametricEQParser {

    private const val PREAMP_KEYWORD = "Preamp:"
    private const val FILTER_KEYWORD = "Filter"
    private const val ENABLED_MARKER = "ON"

    private val NEWLINE: String = System.lineSeparator()

    private val PREAMP_PATTERN = Regex("""\bPreamp:\s*([-+]?\d+\.?\d*)\s*dB""", RegexOption.IGNORE_CASE)
    private val FREQUENCY_PATTERN = Regex("""\bFc\s+([-+]?\d+\.?\d*)\s*Hz""", RegexOption.IGNORE_CASE)
    private val GAIN_PATTERN = Regex("""\bGain\s+([-+]?\d+\.?\d*)\s*dB""", RegexOption.IGNORE_CASE)
    private val QUALITY_PATTERN = Regex("""\bQ\s+([-+]?\d+\.?\d*)""", RegexOption.IGNORE_CASE)

    /** Normative resolution order when a band line contains several type tokens. */
    private val TYPE_PRECEDENCE = listOf(
        FilterType.LSC,
        FilterType.HSC,
        FilterType.PK,
        FilterType.LPQ,
        FilterType.HPQ,
    )

    // ----------------------------------------------------------------- file I/O

    fun parseFile(file: File): ParametricEQ {
        require(file.exists()) { "File does not exist: ${file.absolutePath}" }
        return parseText(file.readText(Charset.defaultCharset()))
    }

    fun parseFile(filePath: String): ParametricEQ = parseFile(File(filePath))

    // ------------------------------------------------------------------- parsing

    fun parseText(content: String): ParametricEQ {
        var preamp = 0.0
        val bands = mutableListOf<ParametricEQBand>()
        val metadata = LinkedHashMap<String, String>()

        for (rawLine in content.lineSequence()) {
            when (val entity = classify(rawLine)) {
                is Entity.Preamp -> preamp = entity.decibels
                is Entity.Band -> bands += entity.band
                is Entity.Metadata -> metadata[entity.key] = entity.value
                Entity.Discard -> Unit
            }
        }

        return ParametricEQ(preamp = preamp, bands = bands, metadata = metadata)
    }

    /** What one trimmed source line contributes to the result. */
    private sealed interface Entity {
        data class Preamp(val decibels: Double) : Entity
        data class Band(val band: ParametricEQBand) : Entity
        data class Metadata(val key: String, val value: String) : Entity
        object Discard : Entity
    }

    private fun classify(rawLine: String): Entity {
        val line = rawLine.trim()
        if (line.isEmpty()) return Entity.Discard

        if (line.startsWith(PREAMP_KEYWORD, ignoreCase = true)) {
            // Preamp lines are never metadata; an unreadable one just contributes nothing.
            val dB = numberAfter(PREAMP_PATTERN, line) ?: return Entity.Discard
            return Entity.Preamp(dB)
        }
        if (line.startsWith(FILTER_KEYWORD, ignoreCase = true)) {
            // Filter lines are all-or-nothing and are never metadata either.
            return parseBand(line)?.let(Entity::Band) ?: Entity.Discard
        }

        val colon = line.indexOf(':')
        if (colon < 0) return Entity.Discard
        return Entity.Metadata(line.substring(0, colon).trim(), line.substring(colon + 1).trim())
    }

    private fun parseBand(line: String): ParametricEQBand? {
        if (!line.contains(ENABLED_MARKER, ignoreCase = true)) return null
        val filterType = TYPE_PRECEDENCE.firstOrNull { line.contains(it.name, ignoreCase = true) }
            ?: return null
        return ParametricEQBand(
            frequency = numberAfter(FREQUENCY_PATTERN, line) ?: return null,
            gain = numberAfter(GAIN_PATTERN, line) ?: return null,
            q = numberAfter(QUALITY_PATTERN, line) ?: return null,
            filterType = filterType,
        )
    }

    private fun numberAfter(pattern: Regex, line: String): Double? =
        pattern.find(line)?.groupValues?.get(1)?.toDoubleOrNull()

    // ------------------------------------------------------------------- writing

    fun toString(eq: ParametricEQ): String =
        terminatedLines(
            listOf(preampLine(eq.preamp)) +
                eq.bands.mapIndexed { index, band -> displayBandLine(index + 1, band) },
        )

    fun toFileFormat(eq: ParametricEQ): String =
        terminatedLines(
            listOf(preampLine(eq.preamp)) +
                eq.bands.mapIndexed { index, band -> fileBandLine(index + 1, band) },
        )

    /** Every emitted line, including the last, ends with the platform separator. */
    private fun terminatedLines(lines: List<String>): String =
        lines.joinToString(separator = NEWLINE, postfix = NEWLINE)

    private fun preampLine(preamp: Double): String = "Preamp: $preamp dB"

    private fun displayBandLine(position: Int, band: ParametricEQBand): String =
        "Filter $position: ${band.filterType} Fc ${band.frequency} Hz Gain ${band.gain} dB Q ${band.q}"

    private fun fileBandLine(position: Int, band: ParametricEQBand): String {
        val formattedQ = String.format(Locale.ROOT, "%.2f", band.q)
        return "Filter $position: ON ${band.filterType} Fc ${band.frequency.toInt()} Hz Gain ${band.gain} dB Q $formattedQ"
    }

    // ---------------------------------------------------------------- validation

    /**
     * Message templates are kept together so the wire format lives in one place; the rules
     * below only decide whether each template applies.
     */
    private object Messages {
        const val PREAMP = "Preamp value %s dB is out of range (-50 to +50 dB)"
        const val NO_BANDS = "EQ profile must have at least one band"
        const val BAND_COUNT = "EQ profile has %d bands, maximum is %d"
        const val FREQUENCY = "Band %d: Frequency %s Hz is out of range (1 to 100000 Hz)"
        const val GAIN = "Band %d: Gain %s dB is out of range (-30 to +30 dB)"
        const val QUALITY = "Band %d: Q factor %s is out of range (0.01 to 20)"
    }

    private const val PREAMP_BOUND = 50.0
    private const val FREQUENCY_MAX = 100_000.0
    private const val GAIN_BOUND = 30.0
    private const val QUALITY_MAX = 20.0

    private val PROFILE_RULES: List<(ParametricEQ) -> String?> = listOf(
        ::preampViolation,
        ::missingBandsViolation,
        ::excessBandsViolation,
    )

    private val BAND_RULES: List<(Int, ParametricEQBand) -> String?> = listOf(
        ::frequencyViolation,
        ::gainViolation,
        ::qualityViolation,
    )

    fun validate(eq: ParametricEQ): List<String> {
        val errors = mutableListOf<String>()
        for (rule in PROFILE_RULES) rule(eq)?.let(errors::add)
        eq.bands.forEachIndexed { index, band ->
            val position = index + 1
            for (rule in BAND_RULES) rule(position, band)?.let(errors::add)
        }
        return errors
    }

    private fun preampViolation(eq: ParametricEQ): String? =
        if (eq.preamp in -PREAMP_BOUND..PREAMP_BOUND) null
        else String.format(Locale.ROOT, Messages.PREAMP, eq.preamp)

    private fun missingBandsViolation(eq: ParametricEQ): String? =
        if (eq.bands.isNotEmpty()) null else Messages.NO_BANDS

    private fun excessBandsViolation(eq: ParametricEQ): String? =
        if (eq.bands.size <= ParametricEQ.MAX_BANDS) null
        else String.format(Locale.ROOT, Messages.BAND_COUNT, eq.bands.size, ParametricEQ.MAX_BANDS)

    private fun frequencyViolation(position: Int, band: ParametricEQBand): String? =
        if (band.frequency > 0.0 && band.frequency <= FREQUENCY_MAX) null
        else String.format(Locale.ROOT, Messages.FREQUENCY, position, band.frequency)

    private fun gainViolation(position: Int, band: ParametricEQBand): String? =
        if (band.gain in -GAIN_BOUND..GAIN_BOUND) null
        else String.format(Locale.ROOT, Messages.GAIN, position, band.gain)

    private fun qualityViolation(position: Int, band: ParametricEQBand): String? =
        if (band.q > 0.0 && band.q <= QUALITY_MAX) null
        else String.format(Locale.ROOT, Messages.QUALITY, position, band.q)
}
