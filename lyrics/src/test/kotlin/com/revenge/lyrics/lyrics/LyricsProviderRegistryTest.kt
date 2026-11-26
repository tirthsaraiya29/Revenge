package com.revenge.lyrics.lyrics

import com.revenge.core.constants.PreferredLyricsProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsProviderRegistryTest {

    private val defaultOrder = listOf(
        "YouLyPlus", "Paxsenix", "Unison", "BetterLyrics", "SimpMusic",
        "LrcLib", "Kugou", "YouTubeSubtitle", "YouTubeMusic",
    )

    @Test
    fun `default order is the specified priority order`() {
        assertEquals(defaultOrder, LyricsProviderRegistry.getDefaultProviderOrder())
    }

    @Test
    fun `blank serialized order deserializes to the default order`() {
        assertEquals(defaultOrder, LyricsProviderRegistry.deserializeProviderOrder(""))
        assertEquals(emptyList<String>(), LyricsProviderRegistry.deserializeProviderOrder(" , "))
    }

    @Test
    fun `deserialization filters unknown keys and keeps duplicates`() {
        assertEquals(
            listOf("Kugou", "LrcLib", "LrcLib"),
            LyricsProviderRegistry.deserializeProviderOrder("Kugou,Unknown,LrcLib,LrcLib"),
        )
    }

    @Test
    fun `ordered names append missing defaults without duplicates`() {
        val ordered = LyricsProviderRegistry.orderedProviderNames("Kugou")

        assertEquals("Kugou", ordered.first())
        assertEquals(9, ordered.size)
        assertEquals(9, ordered.distinct().size)
        assertEquals(defaultOrder.filter { it != "Kugou" }, ordered.drop(1))
    }

    @Test
    fun `serialization filters unknown names`() {
        assertEquals("Kugou,LrcLib", LyricsProviderRegistry.serializeProviderOrder(listOf("Kugou", "Nope", "LrcLib")))
        assertEquals("", LyricsProviderRegistry.serializeProviderOrder(emptyList()))
    }

    @Test
    fun `ordered providers do not append defaults`() {
        val providers = LyricsProviderRegistry.getOrderedProviders("Kugou")
        assertEquals(1, providers.size)
        assertEquals("Kugou", providers.single().name)
    }

    @Test
    fun `registry keys resolve but display names do not`() {
        assertSame(YouTubeSubtitleLyricsProvider, LyricsProviderRegistry.getProviderByName("YouTubeSubtitle"))
        assertNull(LyricsProviderRegistry.getProviderByName("YouTube Subtitle"))
    }

    @Test
    fun `youtube providers expose display names as provider names`() {
        assertEquals("YouTube Subtitle", YouTubeSubtitleLyricsProvider.name)
        assertEquals("YouTube Music", YouTubeLyricsProvider.name)
        assertEquals("Unknown", LyricsProviderRegistry.getDisplayName("Unknown"))
    }

    @Test
    fun `display name table`() {
        assertEquals("PaxSenix", LyricsProviderRegistry.getDisplayName("Paxsenix"))
        assertEquals("Better Lyrics", LyricsProviderRegistry.getDisplayName("BetterLyrics"))
        assertEquals("KuGou", LyricsProviderRegistry.getDisplayName("Kugou"))
        assertEquals("Mystery", LyricsProviderRegistry.getDisplayName("Mystery"))
    }

    @Test
    fun `enum mapping covers all enum values`() {
        assertEquals("LrcLib", LyricsProviderRegistry.getProviderNameForEnum(PreferredLyricsProvider.LRCLIB))
        assertEquals("Kugou", LyricsProviderRegistry.getProviderNameForEnum(PreferredLyricsProvider.KUGOU))
        assertEquals("BetterLyrics", LyricsProviderRegistry.getProviderNameForEnum(PreferredLyricsProvider.BETTER_LYRICS))
        assertEquals("SimpMusic", LyricsProviderRegistry.getProviderNameForEnum(PreferredLyricsProvider.SIMPMUSIC))
        assertEquals("YouLyPlus", LyricsProviderRegistry.getProviderNameForEnum(PreferredLyricsProvider.YOULYPLUS))
        assertEquals("Paxsenix", LyricsProviderRegistry.getProviderNameForEnum(PreferredLyricsProvider.PAXSENIX))
        assertEquals("Unison", LyricsProviderRegistry.getProviderNameForEnum(PreferredLyricsProvider.UNISON))
    }

    @Test
    fun `provider choice prefers priority and blocks on pending`() {
        val order = listOf("A", "B")

        assertEquals(
            ProviderChoice.Pending,
            LyricsProviderRegistry.chooseProvider(order, emptyMap()),
        )
        assertEquals(
            ProviderChoice.Pending,
            LyricsProviderRegistry.chooseProvider(order, mapOf("B" to ProviderOutcome.SYNCED)),
        )
        assertEquals(
            ProviderChoice.Selected("B"),
            LyricsProviderRegistry.chooseProvider(
                order,
                mapOf("A" to ProviderOutcome.FAILED, "B" to ProviderOutcome.SYNCED),
            ),
        )
        assertEquals(
            ProviderChoice.Selected("A"),
            LyricsProviderRegistry.chooseProvider(
                order,
                mapOf("A" to ProviderOutcome.UNSYNCED, "B" to ProviderOutcome.SYNCED),
            ),
        )
        assertEquals(
            ProviderChoice.None,
            LyricsProviderRegistry.chooseProvider(
                order,
                mapOf("A" to ProviderOutcome.FAILED, "B" to ProviderOutcome.FAILED),
            ),
        )
        assertTrue(LyricsProviderRegistry.chooseProvider(emptyList(), emptyMap()) == ProviderChoice.None)
    }
}
