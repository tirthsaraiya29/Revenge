package com.revenge.dsp.core

import com.revenge.dsp.core.models.FilterType
import com.revenge.dsp.core.models.ParametricEQ
import com.revenge.dsp.core.models.ParametricEQBand
import com.revenge.dsp.core.models.SavedEQProfile
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedEQProfileTest {

    @Test
    fun defaultsAreApplied() {
        val profile = SavedEQProfile(id = "id", name = "Flat")
        assertEquals("", profile.deviceModel)
        assertTrue(profile.bands.isEmpty())
        assertEquals(0.0, profile.preamp, 0.0)
        assertFalse(profile.isCustom)
        assertFalse(profile.isActive)
        assertTrue(profile.addedTimestamp > 0)
    }

    @Test
    fun parametricEqProjectsFields() {
        val bands = listOf(ParametricEQBand(100.0, 3.0))
        val profile = SavedEQProfile(id = "id", name = "n", bands = bands, preamp = -4.5)
        val eq = profile.parametricEQ
        assertEquals(profile.preamp, eq.preamp, 0.0)
        assertEquals(profile.bands, eq.bands)
        assertTrue(eq.metadata.isEmpty())
    }

    @Test
    fun savedProfileJsonRoundTrips() {
        val original = SavedEQProfile(
            id = "abc",
            name = "Studio",
            deviceModel = "Pixel 7",
            bands = listOf(
                ParametricEQBand(31.0, 5.2, 1.41, FilterType.PK, true),
                ParametricEQBand(10000.0, -2.0, 0.71, FilterType.HSC, false)
            ),
            preamp = -6.5,
            isCustom = true,
            isActive = true,
            addedTimestamp = 123456789L
        )
        val json = Json.encodeToString(original)
        val restored = Json.decodeFromString<SavedEQProfile>(json)
        assertEquals(original, restored)
    }

    @Test
    fun parametricEqJsonRoundTrips() {
        val original = ParametricEQ(
            preamp = 2.5,
            bands = listOf(ParametricEQBand(440.0, -1.0, 2.0, FilterType.LSC, true)),
            metadata = mapOf("Source" to "AutoEQ", "Device" to "Pixel 7")
        )
        val json = Json.encodeToString(original)
        val restored = Json.decodeFromString<ParametricEQ>(json)
        assertEquals(original, restored)
    }

    @Test
    fun missingOptionalJsonUsesDefaults() {
        val profileJson = """{"id":"x","name":"Legacy"}"""
        val profile = Json.decodeFromString<SavedEQProfile>(profileJson)
        assertEquals("", profile.deviceModel)
        assertTrue(profile.bands.isEmpty())
        assertEquals(0.0, profile.preamp, 0.0)
        assertFalse(profile.isCustom)
        assertFalse(profile.isActive)
        assertTrue(profile.addedTimestamp > 0)

        val eqJson = """{"preamp":0.0,"bands":[{"frequency":100.0,"gain":2.0}]}"""
        val eq = Json.decodeFromString<ParametricEQ>(eqJson)
        assertTrue(eq.metadata.isEmpty())
        val band = eq.bands.single()
        assertEquals(1.41, band.q, 0.0)
        assertEquals(FilterType.PK, band.filterType)
        assertTrue(band.enabled)
    }

    @Test
    fun maxBandsIsTwenty() {
        assertEquals(20, ParametricEQ.MAX_BANDS)
    }
}
