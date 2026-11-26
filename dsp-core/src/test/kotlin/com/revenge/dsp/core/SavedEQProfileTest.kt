package com.revenge.dsp.core

import com.revenge.dsp.core.models.ParametricEQBand
import com.revenge.dsp.core.models.SavedEQProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedEQProfileTest {

    @Test
    fun requiredFieldsOnlyUseDocumentedDefaults() {
        val profile = SavedEQProfile(id = "profile-1", name = "Flat")

        assertEquals("", profile.deviceModel)
        assertTrue(profile.bands.isEmpty())
        assertEquals(0.0, profile.preamp, 0.0)
        assertFalse(profile.isCustom)
        assertFalse(profile.isActive)
        assertTrue(profile.addedTimestamp > 0)
    }

    @Test
    fun parametricEqCarriesPreampAndBandsWithoutCopying() {
        val bands = listOf(ParametricEQBand(frequency = 1000.0, gain = 3.0))
        val profile = SavedEQProfile(id = "profile-2", name = "Custom", preamp = -4.0, bands = bands)

        val eq = profile.parametricEQ
        assertEquals(-4.0, eq.preamp, 0.0)
        assertSame(bands, eq.bands)
        assertTrue(eq.metadata.isEmpty())
    }

    @Test
    fun structuralEqualityCoversAllFields() {
        val first = SavedEQProfile(id = "same", name = "Same", addedTimestamp = 10L)
        val second = SavedEQProfile(id = "same", name = "Same", addedTimestamp = 10L)

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }
}
