package com.revenge.dsp.core

import com.revenge.dsp.core.models.FilterType
import com.revenge.dsp.core.models.ParametricEQ
import com.revenge.dsp.core.models.ParametricEQBand
import com.revenge.dsp.core.parser.ParametricEQParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ParametricEQParserTest {

    @Test
    fun mixedBlockParsesPreampBandsAndIsValid() {
        val text = """
            Preamp: -6.5 dB
            Filter 1: ON PK Fc 100 Hz Gain 3.0 dB Q 1.41
            Filter 2: ON LSC Fc 80 Hz Gain -2.5 dB Q 0.71
            Filter 3: ON HSC Fc 12000 Hz Gain 1.5 dB Q 0.71
        """.trimIndent()

        val eq = ParametricEQParser.parseText(text)
        assertEquals(-6.5, eq.preamp, 1e-9)
        assertEquals(3, eq.bands.size)
        assertEquals(100.0, eq.bands[0].frequency, 1e-9)
        assertEquals(3.0, eq.bands[0].gain, 1e-9)
        assertEquals(1.41, eq.bands[0].q, 1e-9)
        assertEquals(FilterType.PK, eq.bands[0].filterType)
        assertEquals(FilterType.LSC, eq.bands[1].filterType)
        assertEquals(FilterType.HSC, eq.bands[2].filterType)
        assertTrue(ParametricEQParser.validate(eq).isEmpty())
    }

    @Test
    fun disabledBandIsSkippedAndFollowingEnabledBandKept() {
        val text = """
            Filter 1: OFF PK Fc 100 Hz Gain 3.0 dB Q 1.41
            Filter 2: ON PK Fc 200 Hz Gain 4.0 dB Q 1.0
        """.trimIndent()

        val eq = ParametricEQParser.parseText(text)
        assertEquals(1, eq.bands.size)
        assertEquals(200.0, eq.bands[0].frequency, 1e-9)
    }

    @Test
    fun metadataIsCapturedAndUnknownLinesIgnored() {
        val text = """
            Device: Pixel 8
            Source: AutoEQ
            this line has no separator
        """.trimIndent()

        val eq = ParametricEQParser.parseText(text)
        assertEquals("Pixel 8", eq.metadata["Device"])
        assertEquals("AutoEQ", eq.metadata["Source"])
        assertEquals(2, eq.metadata.size)
        assertTrue(eq.bands.isEmpty())
    }

    @Test
    fun bandsMissingQOrWithUnknownTypeAreSkipped() {
        val text = """
            Filter 1: ON PK Fc 100 Hz Gain 3.0 dB
            Filter 2: ON ZZZ Fc 200 Hz Gain 4.0 dB Q 1.0
            Filter 3: ON PK Fc 300 Hz Gain 5.0 dB Q 1.2
        """.trimIndent()

        val eq = ParametricEQParser.parseText(text)
        assertEquals(1, eq.bands.size)
        assertEquals(300.0, eq.bands[0].frequency, 1e-9)
    }

    @Test
    fun lowercaseDirectivesAreAccepted() {
        val text = """
            preamp: -3.0 dB
            filter 1: on lsc fc 60 Hz gain -4.0 dB q 0.7
        """.trimIndent()

        val eq = ParametricEQParser.parseText(text)
        assertEquals(-3.0, eq.preamp, 1e-9)
        assertEquals(1, eq.bands.size)
        assertEquals(FilterType.LSC, eq.bands[0].filterType)
        assertEquals(60.0, eq.bands[0].frequency, 1e-9)
        assertEquals(0.7, eq.bands[0].q, 1e-9)
    }

    @Test
    fun lowPassAndHighPassBandsRoundTripAsEnumValues() {
        val eq = ParametricEQ(preamp = 0.0, bands = listOf(
            ParametricEQBand(frequency = 1000.0, gain = 0.0, q = 1.41, filterType = FilterType.LPQ),
            ParametricEQBand(frequency = 5000.0, gain = 0.0, q = 1.41, filterType = FilterType.HPQ),
        ))

        val reparsed = ParametricEQParser.parseText(ParametricEQParser.toFileFormat(eq))
        assertEquals(FilterType.LPQ, reparsed.bands[0].filterType)
        assertEquals(FilterType.HPQ, reparsed.bands[1].filterType)
    }

    @Test
    fun fileFormatRoundTripsBands() {
        val eq = ParametricEQ(preamp = -3.5, bands = listOf(
            ParametricEQBand(frequency = 1000.0, gain = 2.0, q = 1.41, filterType = FilterType.PK),
            ParametricEQBand(frequency = 100.0, gain = -1.0, q = 0.71, filterType = FilterType.LSC),
        ))

        val formatted = ParametricEQParser.toFileFormat(eq)
        assertTrue(formatted.contains("ON"))
        assertTrue(formatted.contains("Preamp: -3.5 dB"))

        val reparsed = ParametricEQParser.parseText(formatted)
        assertEquals(eq.preamp, reparsed.preamp, 1e-9)
        assertEquals(eq.bands, reparsed.bands)
    }

    @Test
    fun displayFormatOmitsOnMarker() {
        val eq = ParametricEQ(preamp = 1.5, bands = listOf(
            ParametricEQBand(frequency = 440.0, gain = 2.0, q = 0.71, filterType = FilterType.PK),
        ))

        val display = ParametricEQParser.toString(eq)
        assertTrue(display.contains("Preamp: 1.5 dB"))
        assertTrue(display.contains("Filter 1: PK"))
        assertTrue(display.contains("Fc 440.0 Hz"))
        assertFalse(display.contains("ON"))
    }

    @Test
    fun parseFileReadsTextAndMissingFileThrows() {
        val file = File.createTempFile("eq-profile", ".txt")
        try {
            file.writeText("Preamp: 2.0 dB\nFilter 1: ON PK Fc 500 Hz Gain 1.0 dB Q 1.4")
            val eq = ParametricEQParser.parseFile(file)
            assertEquals(2.0, eq.preamp, 1e-9)
            assertEquals(1, eq.bands.size)
        } finally {
            file.delete()
        }

        val missing = File(file.parentFile, "definitely-absent-eq-profile.txt")
        assertThrows(IllegalArgumentException::class.java) {
            ParametricEQParser.parseFile(missing)
        }
    }

    @Test
    fun validateProducesExpectedErrors() {
        val invalid = ParametricEQ(
            preamp = 60.0,
            bands = listOf(ParametricEQBand(frequency = 0.0, gain = 40.0, q = 0.0)),
        )
        val errors = ParametricEQParser.validate(invalid)
        assertEquals(4, errors.size)
        assertTrue(errors.any { it.contains("Preamp") })
        assertTrue(errors.any { it.contains("Frequency") })
        assertTrue(errors.any { it.contains("Gain") })
        assertTrue(errors.any { it.contains("Q factor") })
    }

    @Test
    fun validateRejectsEmptyAndOversizedBandLists() {
        val empty = ParametricEQParser.validate(ParametricEQ(preamp = 0.0, bands = emptyList()))
        assertEquals(1, empty.size)
        assertTrue(empty[0].contains("at least one band"))

        val many = ParametricEQ(
            preamp = 0.0,
            bands = List(21) { ParametricEQBand(frequency = 1000.0, gain = 0.0) },
        )
        val oversized = ParametricEQParser.validate(many)
        assertEquals(1, oversized.size)
        assertTrue(oversized[0].contains("maximum is 20"))
    }
}
