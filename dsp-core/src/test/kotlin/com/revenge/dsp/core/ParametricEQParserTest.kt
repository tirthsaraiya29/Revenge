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
  fun parsesAutoEqBlockWithPreampAndBands() {
    val text =
      """
      Preamp: -6.5 dB
      Filter 1: ON PK Fc 31 Hz Gain 5.2 dB Q 1.41
      Filter 2: ON LSC Fc 105 Hz Gain 3.0 dB Q 0.71
      Filter 3: ON HSC Fc 10000 Hz Gain -2.0 dB Q 0.71
      """
        .trimIndent()

    val eq = ParametricEQParser.parseText(text)

    assertEquals(-6.5, eq.preamp, 0.001)
    assertEquals(3, eq.bands.size)
    assertEquals(FilterType.PK, eq.bands[0].filterType)
    assertEquals(31.0, eq.bands[0].frequency, 0.001)
    assertEquals(5.2, eq.bands[0].gain, 0.001)
    assertEquals(1.41, eq.bands[0].q, 0.001)
    assertEquals(FilterType.LSC, eq.bands[1].filterType)
    assertEquals(FilterType.HSC, eq.bands[2].filterType)
    assertTrue(ParametricEQParser.validate(eq).isEmpty())
  }

  @Test
  fun skipsDisabledBandsAndKeepsEnabledOnes() {
    val text =
      """
      Preamp: -1.0 dB
      Filter 1: OFF PK Fc 100 Hz Gain 2.0 dB Q 1.0
      Filter 2: ON PK Fc 200 Hz Gain 3.0 dB Q 1.0
      """
        .trimIndent()

    val eq = ParametricEQParser.parseText(text)

    assertEquals(1, eq.bands.size)
    assertEquals(200.0, eq.bands[0].frequency, 0.001)
  }

  @Test
  fun capturesMetadataAndIgnoresUnknownLines() {
    val text =
      """
      Preamp: 0 dB
      Device: Pixel 7
      Source: AutoEQ
      Filter 1: ON PK Fc 100 Hz Gain 1.0 dB Q 1.0
      """
        .trimIndent()

    val eq = ParametricEQParser.parseText(text)

    assertEquals("Pixel 7", eq.metadata["Device"])
    assertEquals("AutoEQ", eq.metadata["Source"])
    assertEquals(1, eq.bands.size)
  }

  @Test
  fun skipsMalformedBandLines() {
    val text =
      """
      Filter 1: ON PK Fc 100 Hz Gain 2.0 dB Q 1.0
      Filter 2: ON PK Fc 200 Hz Gain 3.0 dB
      Filter 3: ON ZZZ Fc 300 Hz Gain 4.0 dB Q 1.0
      """
        .trimIndent()

    val eq = ParametricEQParser.parseText(text)

    assertEquals(1, eq.bands.size)
    assertEquals(100.0, eq.bands[0].frequency, 0.001)
  }

  @Test
  fun acceptsLowercaseDirectives() {
    val text =
      """
      preamp: +2.0 dB
      filter 1: on lsc fc 100 hz gain 4.5 db q 0.7
      """
        .trimIndent()

    val eq = ParametricEQParser.parseText(text)

    assertEquals(2.0, eq.preamp, 0.001)
    assertEquals(1, eq.bands.size)
    assertEquals(FilterType.LSC, eq.bands[0].filterType)
    assertEquals(4.5, eq.bands[0].gain, 0.001)
  }

  @Test
  fun parsesLowAndHighPassTypesFromText() {
    val text =
      """
      Filter 1: ON LPQ Fc 80 Hz Gain 0 dB Q 1.0
      Filter 2: ON HPQ Fc 12000 Hz Gain 0 dB Q 1.0
      """
        .trimIndent()

    val eq = ParametricEQParser.parseText(text)

    assertEquals(FilterType.LPQ, eq.bands[0].filterType)
    assertEquals(FilterType.HPQ, eq.bands[1].filterType)
  }

  @Test
  fun fileFormatRoundTripsThroughParser() {
    val original =
      ParametricEQ(
        preamp = -3.5,
        bands =
          listOf(
            ParametricEQBand(100.0, 5.2, 1.41, FilterType.PK),
            ParametricEQBand(105.0, 3.0, 0.71, FilterType.LSC),
            ParametricEQBand(10000.0, -2.0, 0.71, FilterType.HSC),
          ),
      )

    val text = ParametricEQParser.toFileFormat(original)
    val parsed = ParametricEQParser.parseText(text)

    assertTrue(text.contains("ON"))
    assertTrue(text.contains("Preamp: -3.5 dB"))
    assertEquals(-3.5, parsed.preamp, 0.001)
    assertEquals(3, parsed.bands.size)
    assertEquals(original.bands, parsed.bands)
  }

  @Test
  fun displayFormatOmitsOnMarkerButKeepsBandsVisible() {
    val eq = ParametricEQ(preamp = 1.5, bands = listOf(ParametricEQBand(440.0, 2.5, 1.0)))

    val text = ParametricEQParser.toString(eq)

    assertTrue(text.contains("Preamp: 1.5 dB"))
    assertTrue(text.contains("Filter 1: PK"))
    assertTrue(text.contains("Fc 440.0 Hz"))
  }

  @Test
  fun parseFileReadsContentAndRejectsMissingFile() {
    val file = File.createTempFile("revenge-eq", ".txt")
    file.deleteOnExit()
    file.writeText("Preamp: -2.0 dB\nFilter 1: ON PK Fc 250 Hz Gain 1.5 dB Q 1.2\n")

    val eq = ParametricEQParser.parseFile(file)
    assertEquals(-2.0, eq.preamp, 0.001)
    assertEquals(250.0, eq.bands[0].frequency, 0.001)

    val missing = File(file.parentFile, "definitely-missing-${System.nanoTime()}.txt")
    assertThrows(IllegalArgumentException::class.java) {
      ParametricEQParser.parseFile(missing)
    }
  }

  @Test
  fun validateReportsOutOfRangePreampAndBandValues() {
    val eq =
      ParametricEQ(
        preamp = 60.0,
        bands = listOf(ParametricEQBand(frequency = 0.0, gain = 40.0, q = 0.0)),
      )

    val errors = ParametricEQParser.validate(eq)

    assertEquals(4, errors.size)
    assertTrue(errors.any { it.contains("Preamp") })
    assertTrue(errors.any { it.contains("Frequency") })
    assertTrue(errors.any { it.contains("Gain") })
    assertTrue(errors.any { it.contains("Q factor") })
  }

  @Test
  fun validateRejectsEmptyAndOversizedProfiles() {
    val empty = ParametricEQParser.validate(ParametricEQ(preamp = 0.0, bands = emptyList()))
    assertEquals(1, empty.size)
    assertTrue(empty[0].contains("at least one band"))

    val oversizedBands = List(ParametricEQ.MAX_BANDS + 1) { ParametricEQBand(1000.0, 0.0, 1.0) }
    val oversized =
      ParametricEQParser.validate(ParametricEQ(preamp = 0.0, bands = oversizedBands))
    assertEquals(1, oversized.size)
    assertTrue(oversized[0].contains("maximum is ${ParametricEQ.MAX_BANDS}"))
  }
}
