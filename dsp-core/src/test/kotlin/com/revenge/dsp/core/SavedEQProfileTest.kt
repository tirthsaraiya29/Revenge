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

/** Requirement-driven tests for the persisted profile model and its JSON contract. */
class SavedEQProfileTest {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    @Test
    fun `defaults match the documented profile`() {
        val profile = SavedEQProfile(id = "id", name = "Flat")

        assertEquals("", profile.deviceModel)
        assertTrue(profile.bands.isEmpty())
        assertEquals(0.0, profile.preamp, 0.0)
        assertFalse(profile.isCustom)
        assertFalse(profile.isActive)
        assertTrue(profile.addedTimestamp > 0L)
    }

    @Test
    fun `timestamp default is evaluated per construction`() {
        val first = SavedEQProfile(id = "a", name = "A")
        val second = SavedEQProfile(id = "b", name = "B")

        assertTrue(first.addedTimestamp > 0L)
        assertTrue(second.addedTimestamp >= first.addedTimestamp)
    }

    @Test
    fun `parametricEQ projects preamp and bands with empty metadata`() {
        val bands = listOf(ParametricEQBand(frequency = 100.0, gain = 2.0, q = 1.0))
        val profile = SavedEQProfile(id = "id", name = "Name", bands = bands, preamp = -4.0)

        val eq = profile.parametricEQ

        assertEquals(profile.preamp, eq.preamp, 0.0)
        assertEquals(profile.bands, eq.bands)
        assertTrue(eq.metadata.isEmpty())
    }

    @Test
    fun `profile round-trips through JSON`() {
        val profile = SavedEQProfile(
            id = "p1",
            name = "Studio",
            deviceModel = "Pixel 7",
            bands = listOf(
                ParametricEQBand(100.0, 2.0, 1.41, FilterType.LSC, true),
                ParametricEQBand(10000.0, -3.0, 0.71, FilterType.HSC, false),
            ),
            preamp = -3.0,
            isCustom = true,
            isActive = true,
            addedTimestamp = 123456789L,
        )

        val encoded = json.encodeToString(profile)

        assertEquals(profile, json.decodeFromString<SavedEQProfile>(encoded))
        assertFalse(encoded.contains("parametricEQ"))
    }

    @Test
    fun `parametric EQ round-trips through JSON preserving metadata and enum names`() {
        val eq = ParametricEQ(
            preamp = 1.5,
            bands = listOf(ParametricEQBand(440.0, 0.0, 1.41, FilterType.PK, true)),
            metadata = mapOf("Source" to "AutoEQ"),
        )

        val encoded = json.encodeToString(eq)

        assertTrue(encoded.contains("\"PK\""))
        assertEquals(eq, json.decodeFromString<ParametricEQ>(encoded))
    }

    @Test
    fun `band defaults apply when optional JSON keys are absent`() {
        val decoded = json.decodeFromString<ParametricEQBand>(
            """{"frequency":1000.0,"gain":2.5}""",
        )

        assertEquals(1.41, decoded.q, 0.0)
        assertEquals(FilterType.PK, decoded.filterType)
        assertTrue(decoded.enabled)
    }

    @Test
    fun `parametric EQ defaults metadata to an empty map when absent`() {
        val decoded = json.decodeFromString<ParametricEQ>(
            """{"preamp":0.0,"bands":[{"frequency":1000.0,"gain":2.5}]}""",
        )

        assertTrue(decoded.metadata.isEmpty())
        assertEquals(1, decoded.bands.size)
    }

    @Test
    fun `profile defaults apply to older JSON documents`() {
        val decoded = json.decodeFromString<SavedEQProfile>(
            """{"id":"old","name":"Legacy","bands":[{"frequency":1000.0,"gain":2.5}]}""",
        )

        assertEquals("", decoded.deviceModel)
        assertEquals(0.0, decoded.preamp, 0.0)
        assertFalse(decoded.isCustom)
        assertFalse(decoded.isActive)
        assertTrue(decoded.addedTimestamp > 0L)
        assertEquals(1.41, decoded.bands.single().q, 0.0)
        assertEquals(FilterType.PK, decoded.bands.single().filterType)
        assertTrue(decoded.bands.single().enabled)
    }

    @Test
    fun `MAX_BANDS is twenty`() {
        assertEquals(20, ParametricEQ.MAX_BANDS)
    }
}
