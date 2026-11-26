package com.revenge.dsp.core

import com.revenge.dsp.core.models.FilterType
import com.revenge.dsp.core.models.ParametricEQ
import com.revenge.dsp.core.models.ParametricEQBand
import com.revenge.dsp.core.parser.ParametricEQParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ParametricEQParserTest {

    @Test
    fun parsesAutoeqBlock() {
        val eq = ParametricEQParser.parseText(
            """
            Preamp: -6.5 dB
            Filter 1: ON PK Fc 31 Hz Gain 5.2 dB Q 1.41
            Filter 2: ON LSC Fc 105 Hz Gain 3.0 dB Q 0.71
            Filter 3: ON HSC Fc 10000 Hz Gain -2.0 dB Q 0.71
            """.trimIndent()
        )

        assertEquals(-6.5, eq.preamp, 0.0)
        assertEquals(3, eq.bands.size)
        assertEquals(FilterType.PK, eq.bands[0].filterType)
        assertEquals(31.0, eq.bands[0].frequency, 0.0)
        assertEquals(5.2, eq.bands[0].gain, 0.0)
        assertEquals(1.41, eq.bands[0].q, 0.0)
        assertEquals(FilterType.LSC, eq.bands[1].filterType)
        assertEquals(105.0, eq.bands[1].frequency, 0.0)
        assertEquals(FilterType.HSC, eq.bands[2].filterType)
        assertEquals(-2.0, eq.bands[2].gain, 0.0)
        assertTrue(eq.bands.all { it.enabled })
        assertTrue(ParametricEQParser.validate(eq).isEmpty())
    }

    @Test
    fun parsesLowercaseDirectives() {
        val eq = ParametricEQParser.parseText(
            """
            preamp: +2.0 dB
            filter 1: on lsc fc 100 hz gain 4.5 db q 0.7
            """.trimIndent()
        )

        assertEquals(2.0, eq.preamp, 0.0)
        assertEquals(1, eq.bands.size)
        val band = eq.bands.single()
        assertEquals(FilterType.LSC, band.filterType)
        assertEquals(100.0, band.frequency, 0.0)
        assertEquals(4.5, band.gain, 0.0)
        assertEquals(0.7, band.q, 0.0)
    }

    @Test
    fun recognisesLpqAndHpq() {
        val eq = ParametricEQParser.parseText(
            """
            Filter 1: ON LPQ Fc 1000 Hz Gain 2 dB Q 1
            Filter 2: ON HPQ Fc 2000 Hz Gain -2 dB Q 1
            """.trimIndent()
        )
        assertEquals(listOf(FilterType.LPQ, FilterType.HPQ), eq.bands.map { it.filterType })
    }

    @Test
    fun dropsDisabledBands() {
        val eq = ParametricEQParser.parseText(
            """
            Filter 1: OFF PK Fc 1000 Hz Gain 3 dB Q 1
            Filter 2: ON PK Fc 2000 Hz Gain 3 dB Q 1
            """.trimIndent()
        )
        assertEquals(1, eq.bands.size)
        assertEquals(2000.0, eq.bands.single().frequency, 0.0)
    }

    @Test
    fun skipsMalformedFilterLines() {
        val eq = ParametricEQParser.parseText(
            """
            Filter 1: ON PK Fc 1000 Hz Gain 3 dB
            Filter 2: ON XYZ Fc 1000 Hz Gain 3 dB Q 1
            Filter 3: ON PK Fc 500 Hz Gain 1 dB Q 2
            """.trimIndent()
        )
        assertEquals(1, eq.bands.size)
        assertEquals(500.0, eq.bands.single().frequency, 0.0)
    }

    @Test
    fun capturesMetadataButNotPreamp() {
        val eq = ParametricEQParser.parseText(
            """
            Device: Pixel 7
            Source: AutoEQ
            Preamp: -3 dB
            Filter 1: ON PK Fc 1000 Hz Gain 2 dB Q 1
            """.trimIndent()
        )
        assertEquals("Pixel 7", eq.metadata["Device"])
        assertEquals("AutoEQ", eq.metadata["Source"])
        assertTrue(eq.metadata.keys.none { it.equals("Preamp", ignoreCase = true) })
        assertEquals(-3.0, eq.preamp, 0.0)
        assertEquals(1, eq.bands.size)
    }

    @Test
    fun missingOrInvalidPreampBecomesZero() {
        assertEquals(0.0, ParametricEQParser.parseText("Filter 1: ON PK Fc 500 Hz Gain 1 dB Q 1").preamp, 0.0)
        assertEquals(0.0, ParametricEQParser.parseText("Preamp: -3.5").preamp, 0.0)
    }

    @Test
    fun emptyInputIsSafe() {
        val eq = ParametricEQParser.parseText("")
        assertEquals(0.0, eq.preamp, 0.0)
        assertTrue(eq.bands.isEmpty())
        assertTrue(eq.metadata.isEmpty())
    }

    @Test
    fun fileFormatRoundTrips() {
        val eq = ParametricEQ(
            preamp = -3.5,
            bands = listOf(
                ParametricEQBand(100.0, 2.5, 1.41, FilterType.PK),
                ParametricEQBand(10000.0, -1.25, 0.71, FilterType.HSC)
            )
        )
        val formatted = ParametricEQParser.toFileFormat(eq)
        assertTrue(formatted.contains("ON"))
        assertTrue(formatted.contains("Preamp: -3.5 dB"))

        val parsed = ParametricEQParser.parseText(formatted)
        assertEquals(eq.preamp, parsed.preamp, 1e-9)
        assertEquals(eq.bands.size, parsed.bands.size)
        eq.bands.zip(parsed.bands).forEach { (expected, actual) ->
            assertEquals(expected.frequency, actual.frequency, 0.0)
            assertEquals(expected.gain, actual.gain, 0.01)
            assertEquals(expected.q, actual.q, 0.005)
            assertEquals(expected.filterType, actual.filterType)
            assertTrue(actual.enabled)
        }
    }

    @Test
    fun displayFormatOmitsOnMarker() {
        val eq = ParametricEQ(preamp = 1.5, bands = listOf(ParametricEQBand(440.0, 0.0, 1.41, FilterType.PK)))
        val display = ParametricEQParser.toString(eq)
        assertTrue(display.contains("Preamp: 1.5 dB"))
        assertTrue(display.contains("Filter 1: PK"))
        assertTrue(display.contains("Fc 440.0 Hz"))
        assertTrue(!display.contains(" ON "))
    }

    @Test
    fun fileFormatTruncatesFrequencyAndFormatsQ() {
        val eq = ParametricEQ(preamp = 0.0, bands = listOf(ParametricEQBand(100.9, 0.0, 1.41)))
        val formatted = ParametricEQParser.toFileFormat(eq)
        assertTrue(formatted.contains("Fc 100 Hz"))
        assertTrue(formatted.contains("Q 1.41"))
    }

    @Test
    fun reportsAllOutOfRangeValues() {
        val eq = ParametricEQ(
            preamp = 60.0,
            bands = listOf(ParametricEQBand(frequency = 0.0, gain = 40.0, q = 0.0))
        )
        val errors = ParametricEQParser.validate(eq)
        assertEquals(4, errors.size)
        assertTrue(errors.any { it.contains("Preamp") })
        assertTrue(errors.any { it.contains("Frequency") })
        assertTrue(errors.any { it.contains("Gain") })
        assertTrue(errors.any { it.contains("Q factor") })
    }

    @Test
    fun detectsEmptyAndOversizedBandLists() {
        val empty = ParametricEQParser.validate(ParametricEQ(preamp = 0.0, bands = emptyList()))
        assertEquals(1, empty.size)
        assertTrue(empty.single().contains("at least one band"))

        val bands = List(ParametricEQ.MAX_BANDS + 1) { ParametricEQBand(100.0, 0.0) }
        val oversized = ParametricEQParser.validate(ParametricEQ(preamp = 0.0, bands = bands))
        assertEquals(1, oversized.size)
        assertTrue(oversized.single().contains("maximum is 20"))
    }

    @Test
    fun acceptsBoundaryValues() {
        val band = ParametricEQBand(frequency = 100000.0, gain = 30.0, q = 20.0)
        assertTrue(ParametricEQParser.validate(ParametricEQ(50.0, listOf(band))).isEmpty())
        assertTrue(ParametricEQParser.validate(ParametricEQ(-50.0, listOf(band))).isEmpty())

        val negativeBand = ParametricEQBand(frequency = 1.0, gain = -30.0, q = 0.01)
        assertTrue(ParametricEQParser.validate(ParametricEQ(0.0, listOf(negativeBand))).isEmpty())
    }

    @Test
    fun parseFileReadsExistingFile() {
        val file = File.createTempFile("eq-parser", ".txt")
        try {
            file.writeText(
                """
                Preamp: -2.0 dB
                Filter 1: ON PK Fc 1000 Hz Gain 3 dB Q 1.41
                """.trimIndent()
            )
            val eq = ParametricEQParser.parseFile(file)
            assertEquals(-2.0, eq.preamp, 0.0)
            assertEquals(1, eq.bands.size)
            assertEquals(1000.0, eq.bands.single().frequency, 0.0)
            assertEquals(eq, ParametricEQParser.parseFile(file.absolutePath))
        } finally {
            file.delete()
        }
    }

    @Test
    fun parseFileRejectsMissingFile() {
        val missing = File(File(System.getProperty("java.io.tmpdir")), "missing-eq-${System.nanoTime()}.txt")
        val error = assertThrows(IllegalArgumentException::class.java) {
            ParametricEQParser.parseFile(missing)
        }
        assertTrue(error.message!!.contains("File does not exist:"))
        assertTrue(error.message!!.contains(missing.absolutePath))
    }
}
