package com.revenge.dsp.core

import com.revenge.dsp.core.models.FilterType
import com.revenge.dsp.core.models.ParametricEQBand
import com.revenge.dsp.core.models.SavedEQProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedEQProfileTest {

  @Test
  fun defaultsMatchAppExpectations() {
    val profile = SavedEQProfile(id = "id", name = "Flat")

    assertEquals("", profile.deviceModel)
    assertTrue(profile.bands.isEmpty())
    assertEquals(0.0, profile.preamp, 0.0)
    assertEquals(false, profile.isCustom)
    assertEquals(false, profile.isActive)
    assertTrue(profile.addedTimestamp > 0L)
  }

  @Test
  fun parametricEqViewCarriesPreampAndBands() {
    val bands =
      listOf(
        ParametricEQBand(1000.0, 3.0, 1.41, FilterType.PK),
        ParametricEQBand(8000.0, -2.0, 0.7, FilterType.HSC),
      )
    val profile =
      SavedEQProfile(
        id = "custom_1",
        name = "Custom",
        deviceModel = "Equalizer",
        bands = bands,
        preamp = -4.0,
        isCustom = true,
        isActive = true,
      )

    val eq = profile.parametricEQ

    assertEquals(-4.0, eq.preamp, 0.0)
    assertEquals(bands, eq.bands)
    assertTrue(eq.metadata.isEmpty())
  }
}
