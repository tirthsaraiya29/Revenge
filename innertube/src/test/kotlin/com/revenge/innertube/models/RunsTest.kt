package com.revenge.innertube.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class RunsTest {
    @Test
    fun compactCount() {
        assertEquals("1.2M", Runs(listOf(Run("1.2M views"))).extractCountText())
    }

    @Test
    fun groupedCount() {
        assertEquals("1,234", Runs(listOf(Run("1,234 likes"))).extractCountText())
    }

    @Test
    fun noCount() {
        assertNull(Runs(listOf(Run("no numbers here"))).extractCountText())
    }

    @Test
    fun nullRuns() {
        assertNull((null as Runs?).extractCountText())
    }

    @Test
    fun suffixIsRetainedForCjk() {
        assertEquals("1.2万", Runs(listOf(Run("1.2万 views"))).extractCountText())
    }

    @Test
    fun separatorSplitsIntoGroups() {
        val groups = listOf(Run("Song"), Run(" • "), Run("Artist")).splitBySeparator()
        assertEquals(2, groups.size)
        assertEquals(listOf("Song"), groups[0].map { it.text })
        assertEquals(listOf("Artist"), groups[1].map { it.text })
    }

    @Test
    fun oddElementsKeepsEvenIndices() {
        val a = Run("A")
        val b = Run("B")
        val sep = Run(" • ")
        assertEquals(listOf(a, b), listOf(a, sep, b).oddElements())
    }

    @Test
    fun cleanDropsPlainLeadGroup() {
        val cleaned = listOf(
            listOf(Run("Plain artist")),
            listOf(Run("Song")),
        ).clean()
        assertEquals(1, cleaned.size)
        assertEquals("Song", cleaned[0][0].text)
    }

    @Test
    fun cleanKeepsLinkedLeadGroup() {
        val groups = listOf(
            listOf(Run("Linked artist", NavigationEndpoint())),
            listOf(Run("Song")),
        )
        assertSame(groups, groups.clean())
    }

    @Test
    fun cleanKeepsMultiArtistLeadGroup() {
        val groups = listOf(
            listOf(Run("A & B")),
            listOf(Run("Song")),
        )
        assertSame(groups, groups.clean())
    }
}
