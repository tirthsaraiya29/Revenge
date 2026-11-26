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

/** Requirement-driven tests for the AutoEQ parser, derived from the module spec. */
class ParametricEQParserTest {

    // ------------------------------------------------------------------ happy path

    @Test
    fun `parses a standard AutoEQ block in file order`() {
        val text = """
            Preamp: -6.5 dB
            Filter 1: ON PK Fc 31 Hz Gain 5.2 dB Q 1.41
            Filter 2: ON LSC Fc 105 Hz Gain 3.0 dB Q 0.71
            Filter 3: ON HSC Fc 10000 Hz Gain -2.0 dB Q 0.71
        """.trimIndent()

        val eq = ParametricEQParser.parseText(text)

        assertEquals(-6.5, eq.preamp, 1e-9)
        assertEquals(3, eq.bands.size)
        assertEquals(
            listOf(FilterType.PK, FilterType.LSC, FilterType.HSC),
            eq.bands.map { it.filterType },
        )
        assertEquals(listOf(31.0, 105.0, 10000.0), eq.bands.map { it.frequency })
        assertEquals(listOf(5.2, 3.0, -2.0), eq.bands.map { it.gain })
        assertEquals(listOf(1.41, 0.71, 0.71), eq.bands.map { it.q })
        assertTrue(eq.bands.all { it.enabled })
        assertTrue(ParametricEQParser.validate(eq).isEmpty())
    }

    @Test
    fun `directives are case insensitive`() {
        val text = "preamp: +2.0 dB\nfilter 1: on lsc fc 100 hz gain 4.5 db q 0.7"

        val eq = ParametricEQParser.parseText(text)

        assertEquals(2.0, eq.preamp, 1e-9)
        assertEquals(1, eq.bands.size)
        assertEquals(FilterType.LSC, eq.bands[0].filterType)
        assertEquals(100.0, eq.bands[0].frequency, 1e-9)
        assertEquals(4.5, eq.bands[0].gain, 1e-9)
        assertEquals(0.7, eq.bands[0].q, 1e-9)
    }

    @Test
    fun `LPQ and HPQ map to their own enum values in order`() {
        val text = """
            Filter 1: ON LPQ Fc 10000 Hz Gain 2.0 dB Q 1.0
            Filter 2: ON HPQ Fc 20 Hz Gain 2.0 dB Q 1.0
        """.trimIndent()

        val eq = ParametricEQParser.parseText(text)

        assertEquals(listOf(FilterType.LPQ, FilterType.HPQ), eq.bands.map { it.filterType })
    }

    @Test
    fun `type tokens resolve by the documented precedence`() {
        // Both PK and LSC appear; LSC wins.
        val text = "Filter 1: ON PK LSC Fc 100 Hz Gain 1.0 dB Q 1.0"

        val eq = ParametricEQParser.parseText(text)

        assertEquals(FilterType.LSC, eq.bands.single().filterType)
    }

    // ------------------------------------------------------------------- tolerance

    @Test
    fun `disabled bands are dropped entirely`() {
        val text = """
            Filter 1: OFF PK Fc 100 Hz Gain 3.0 dB Q 1.0
            Filter 2: ON PK Fc 200 Hz Gain 3.0 dB Q 1.0
        """.trimIndent()

        val eq = ParametricEQParser.parseText(text)

        assertEquals(1, eq.bands.size)
        assertEquals(200.0, eq.bands[0].frequency, 1e-9)
    }

    @Test
    fun `malformed band lines are skipped whole`() {
        val text = """
            Filter 1: ON PK Fc 1000 Hz Gain 3.0 dB
            Filter 2: ON XYZ Fc 1000 Hz Gain 3.0 dB Q 1.0
            Filter 3: ON PK Fc 2000 Hz Gain 3.0 dB Q 1.0
        """.trimIndent()

        val eq = ParametricEQParser.parseText(text)

        assertEquals(1, eq.bands.size)
        assertEquals(2000.0, eq.bands[0].frequency, 1e-9)
    }

    @Test
    fun `metadata keeps unknown lines, trims, and last key wins`() {
        val text = """
            Device:   Pixel 7
            Source: AutoEQ
            Source: AutoEQ v2
            Preamp: -5.0 dB
            Filter 1: ON PK Fc 1000 Hz Gain 1.0 dB Q 1.0
            just a note without a colon
        """.trimIndent()

        val eq = ParametricEQParser.parseText(text)

        assertEquals("Pixel 7", eq.metadata["Device"])
        assertEquals("AutoEQ v2", eq.metadata["Source"])
        assertFalse(eq.metadata.containsKey("Preamp"))
        assertEquals(1, eq.bands.size)
    }

    @Test
    fun `missing or unreadable preamp defaults to zero`() {
        assertEquals(
            0.0,
            ParametricEQParser.parseText("Filter 1: ON PK Fc 100 Hz Gain 1.0 dB Q 1.0").preamp,
            0.0,
        )
        assertEquals(
            0.0,
            ParametricEQParser.parseText("Preamp: -3.0\nFilter 1: ON PK Fc 100 Hz Gain 1.0 dB Q 1.0").preamp,
            0.0,
        )
    }

    @Test
    fun `empty input yields an empty equalizer`() {
        val eq = ParametricEQParser.parseText("")

        assertEquals(0.0, eq.preamp, 0.0)
        assertTrue(eq.bands.isEmpty())
        assertTrue(eq.metadata.isEmpty())
    }

    @Test
    fun `exponent and leading-dot numbers do not match`() {
        val eq = ParametricEQParser.parseText("Filter 1: ON PK Fc 1e3 Hz Gain .5 dB Q 1.0")

        assertTrue(eq.bands.isEmpty())
    }

    // --------------------------------------------------------------------- writers

    @Test
    fun `file format emits ON, truncates frequency, and rounds Q to two decimals`() {
        val eq = ParametricEQ(
            preamp = -3.5,
            bands = listOf(
                ParametricEQBand(frequency = 100.9, gain = 2.0, q = 1.41, filterType = FilterType.PK),
                ParametricEQBand(frequency = 105.0, gain = -2.0, q = 0.7, filterType = FilterType.LSC),
            ),
        )

        val text = ParametricEQParser.toFileFormat(eq)

        assertTrue(text.contains("Preamp: -3.5 dB"))
        assertTrue(text.contains("Filter 1: ON PK Fc 100 Hz Gain 2.0 dB Q 1.41"))
        assertTrue(text.contains("Filter 2: ON LSC Fc 105 Hz Gain -2.0 dB Q 0.70"))
        assertTrue(text.endsWith(System.lineSeparator()))
    }

    @Test
    fun `file format round-trips through the parser`() {
        val eq = ParametricEQ(
            preamp = -3.5,
            bands = listOf(
                ParametricEQBand(frequency = 100.0, gain = 2.0, q = 1.41, filterType = FilterType.PK),
                ParametricEQBand(frequency = 105.0, gain = 3.0, q = 0.71, filterType = FilterType.LSC),
            ),
        )

        val reparsed = ParametricEQParser.parseText(ParametricEQParser.toFileFormat(eq))

        assertEquals(eq.preamp, reparsed.preamp, 1e-6)
        assertEquals(eq.bands.size, reparsed.bands.size)
        for (i in eq.bands.indices) {
            assertEquals(eq.bands[i].frequency, reparsed.bands[i].frequency, 1e-9)
            assertEquals(eq.bands[i].gain, reparsed.bands[i].gain, 1e-9)
            assertEquals(eq.bands[i].filterType, reparsed.bands[i].filterType)
            assertEquals(eq.bands[i].q, reparsed.bands[i].q, 0.005)
            assertTrue(reparsed.bands[i].enabled)
        }
    }

    @Test
    fun `display format omits ON and metadata`() {
        val eq = ParametricEQ(
            preamp = 1.5,
            bands = listOf(ParametricEQBand(frequency = 440.0, gain = 0.0, q = 1.41)),
            metadata = mapOf("Device" to "Pixel"),
        )

        val text = ParametricEQParser.toString(eq)

        assertTrue(text.contains("Preamp: 1.5 dB"))
        assertTrue(text.contains("Filter 1: PK"))
        assertTrue(text.contains("Fc 440.0 Hz"))
        assertFalse(text.contains(" ON "))
        assertFalse(text.contains("Device"))
        assertTrue(text.endsWith(System.lineSeparator()))
    }

    @Test
    fun `display format with no bands is only the preamp line`() {
        val text = ParametricEQParser.toString(ParametricEQ(preamp = 0.0, bands = emptyList()))

        assertEquals("Preamp: 0.0 dB" + System.lineSeparator(), text)
    }

    // ------------------------------------------------------------------ validation

    @Test
    fun `reports every violation in the documented order with exact text`() {
        val eq = ParametricEQ(
            preamp = 60.0,
            bands = listOf(ParametricEQBand(frequency = 0.0, gain = 40.0, q = 0.0)),
        )

        val errors = ParametricEQParser.validate(eq)

        assertEquals(
            listOf(
                "Preamp value 60.0 dB is out of range (-50 to +50 dB)",
                "Band 1: Frequency 0.0 Hz is out of range (1 to 100000 Hz)",
                "Band 1: Gain 40.0 dB is out of range (-30 to +30 dB)",
                "Band 1: Q factor 0.0 is out of range (0.01 to 20)",
            ),
            errors,
        )
    }

    @Test
    fun `empty band list produces exactly the empty-profile error`() {
        val errors = ParametricEQParser.validate(ParametricEQ(preamp = 0.0, bands = emptyList()))

        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("at least one band"))
    }

    @Test
    fun `oversized band list produces exactly the maximum error`() {
        val bands = List(ParametricEQ.MAX_BANDS + 1) {
            ParametricEQBand(frequency = 1000.0, gain = 0.0, q = 1.0)
        }

        val errors = ParametricEQParser.validate(ParametricEQ(preamp = 0.0, bands = bands))

        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("maximum is 20"))
    }

    @Test
    fun `boundary values are accepted`() {
        val bands = listOf(
            ParametricEQBand(frequency = 100_000.0, gain = 30.0, q = 20.0),
            ParametricEQBand(frequency = 0.01, gain = -30.0, q = 0.01),
        )

        assertTrue(ParametricEQParser.validate(ParametricEQ(preamp = 50.0, bands = bands)).isEmpty())
        assertTrue(ParametricEQParser.validate(ParametricEQ(preamp = -50.0, bands = bands)).isEmpty())
    }

    @Test
    fun `validation reports per-band errors for each band`() {
        val bands = listOf(
            ParametricEQBand(frequency = 1000.0, gain = 0.0, q = 1.0),
            ParametricEQBand(frequency = -1.0, gain = 31.0, q = 21.0),
        )

        val errors = ParametricEQParser.validate(ParametricEQ(preamp = 0.0, bands = bands))

        assertEquals(3, errors.size)
        assertTrue(errors[0].startsWith("Band 2: Frequency"))
        assertTrue(errors[1].startsWith("Band 2: Gain"))
        assertTrue(errors[2].startsWith("Band 2: Q factor"))
    }

    // ---------------------------------------------------------------------- file I/O

    @Test
    fun `parseFile reads a real text file`() {
        val file = File.createTempFile("revenge-eq", ".txt")
        try {
            file.writeText(
                "Preamp: -2.0 dB\nFilter 1: ON PK Fc 250 Hz Gain 1.5 dB Q 2.0\n",
            )

            val eq = ParametricEQParser.parseFile(file)

            assertEquals(-2.0, eq.preamp, 1e-9)
            assertEquals(1, eq.bands.size)
            assertEquals(250.0, eq.bands[0].frequency, 1e-9)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `parseFile String overload behaves like the File overload`() {
        val file = File.createTempFile("revenge-eq", ".txt")
        try {
            file.writeText("Preamp: 1.0 dB\n")

            assertEquals(1.0, ParametricEQParser.parseFile(file.absolutePath).preamp, 1e-9)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `parseFile rejects a missing file with the absolute path`() {
        val missing = File(System.getProperty("java.io.tmpdir"), "revenge-missing-${System.nanoTime()}.txt")

        val error = assertThrows(IllegalArgumentException::class.java) {
            ParametricEQParser.parseFile(missing)
        }

        assertTrue(error.message!!.contains("File does not exist:"))
        assertTrue(error.message!!.contains(missing.absolutePath))
    }

    @Test
    fun `parseFile String overload rejects a missing path`() {
        val missing = File(System.getProperty("java.io.tmpdir"), "revenge-missing-${System.nanoTime()}.txt")

        val error = assertThrows(IllegalArgumentException::class.java) {
            ParametricEQParser.parseFile(missing.absolutePath)
        }

        assertTrue(error.message!!.contains("File does not exist:"))
    }
}
