package com.revenge.lyrics.lyrics

import com.revenge.core.constants.PreferredLyricsProvider

object LyricsProviderRegistry {

    private val registrationOrder = listOf(
        "YouLyPlus",
        "Paxsenix",
        "BetterLyrics",
        "Unison",
        "SimpMusic",
        "LrcLib",
        "Kugou",
        "YouTubeSubtitle",
        "YouTubeMusic",
    )

    private val defaultOrder = listOf(
        "YouLyPlus",
        "Paxsenix",
        "Unison",
        "BetterLyrics",
        "SimpMusic",
        "LrcLib",
        "Kugou",
        "YouTubeSubtitle",
        "YouTubeMusic",
    )

    private val providers: Map<String, LyricsProvider> = linkedMapOf(
        "YouLyPlus" to YouLyPlusLyricsProvider,
        "Paxsenix" to PaxSenixLyricsProvider,
        "BetterLyrics" to BetterLyricsProvider,
        "Unison" to UnisonLyricsProvider,
        "SimpMusic" to SimpMusicLyricsProvider,
        "LrcLib" to LrcLibLyricsProvider,
        "Kugou" to KuGouLyricsProvider,
        "YouTubeSubtitle" to YouTubeSubtitleLyricsProvider,
        "YouTubeMusic" to YouTubeLyricsProvider,
    )

    private val displayNames = mapOf(
        "YouLyPlus" to "YouLyPlus",
        "Paxsenix" to "PaxSenix",
        "BetterLyrics" to "Better Lyrics",
        "Unison" to "Unison",
        "SimpMusic" to "SimpMusic",
        "LrcLib" to "LrcLib",
        "Kugou" to "KuGou",
        "YouTubeSubtitle" to "YouTube Subtitle",
        "YouTubeMusic" to "YouTube Music",
    )

    private val enumKeys = mapOf(
        PreferredLyricsProvider.LRCLIB to "LrcLib",
        PreferredLyricsProvider.KUGOU to "Kugou",
        PreferredLyricsProvider.BETTER_LYRICS to "BetterLyrics",
        PreferredLyricsProvider.SIMPMUSIC to "SimpMusic",
        PreferredLyricsProvider.YOULYPLUS to "YouLyPlus",
        PreferredLyricsProvider.PAXSENIX to "Paxsenix",
        PreferredLyricsProvider.UNISON to "Unison",
    )

    val providerNames: List<String> = registrationOrder

    fun getProviderByName(name: String): LyricsProvider? = providers[name]

    fun getDefaultProviderOrder(): List<String> = defaultOrder

    fun deserializeProviderOrder(orderString: String): List<String> {
        if (orderString.isBlank()) return defaultOrder
        return orderString.split(",")
            .map { it.trim() }
            .filter { providers.containsKey(it) }
    }

    fun serializeProviderOrder(order: List<String>): String =
        order.filter { providers.containsKey(it) }.joinToString(",")

    fun orderedProviderNames(orderString: String): List<String> {
        val saved = deserializeProviderOrder(orderString).distinct()
        return saved + defaultOrder.filter { it !in saved }
    }

    fun getOrderedProviders(orderString: String): List<LyricsProvider> =
        deserializeProviderOrder(orderString).mapNotNull(::getProviderByName)

    fun getProviderNameForEnum(enum: PreferredLyricsProvider): String =
        enumKeys[enum] ?: throw NoSuchElementException("Unmapped lyrics provider: $enum")

    fun getDisplayName(name: String): String = displayNames[name] ?: name

    fun chooseProvider(
        order: List<String>,
        outcomes: Map<String, ProviderOutcome>,
    ): ProviderChoice {
        for (name in order) {
            when (outcomes[name]) {
                null -> return ProviderChoice.Pending
                ProviderOutcome.FAILED -> Unit
                ProviderOutcome.SYNCED, ProviderOutcome.UNSYNCED -> return ProviderChoice.Selected(name)
            }
        }
        return ProviderChoice.None
    }
}
