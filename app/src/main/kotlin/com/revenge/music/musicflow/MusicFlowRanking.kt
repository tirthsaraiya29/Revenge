package com.revenge.music.musicflow

/** Canonical identity shared with the taste engine and play statistics. */
fun trackKeyOf(title: String, artist: String): String =
    "${title.trim()}|${artist.trim()}".lowercase()

fun trackKeyOf(item: MusicFlowItem): String =
    trackKeyOf(item.song.title, item.song.artists.joinToString(", ") { it.name })

data class MusicFlowCandidateFilter(
    val seenSongIds: Set<String> = emptySet(),
    val seenTrackKeys: Set<String> = emptySet(),
    val excludedTrackKeys: Set<String> = emptySet(),
    val hiddenSongIds: Set<String> = emptySet(),
    val recentSongIds: Set<String> = emptySet(),
)

fun filterMusicFlowCandidates(
    items: List<MusicFlowItem>,
    filter: MusicFlowCandidateFilter,
): List<MusicFlowItem> {
    val batchIds = HashSet<String>()
    val batchKeys = HashSet<String>()
    val result = ArrayList<MusicFlowItem>(items.size)
    for (item in items) {
        val id = item.songId
        if (id.isBlank()) continue
        val key = trackKeyOf(item)
        if (id in filter.seenSongIds) continue
        if (key in filter.seenTrackKeys) continue
        if (key in filter.excludedTrackKeys) continue
        if (id in filter.hiddenSongIds) continue
        if (id in filter.recentSongIds) continue
        if (!batchIds.add(id)) continue
        if (!batchKeys.add(key)) continue
        result += item
    }
    return result
}

data class MusicFlowRankingConfig(
    val artistSpacing: Int = 2,
    val likedArtistBoost: Double = 1.0,
    val lessLikeThisPenalty: Double = 2.5,
    val positionDecay: Double = 0.02,
    val sourceWeights: Map<MusicFlowSource, Double> = DEFAULT_WEIGHTS,
) {
    companion object {
        val DEFAULT_WEIGHTS: Map<MusicFlowSource, Double> = mapOf(
            MusicFlowSource.HISTORY to 0.6,
            MusicFlowSource.LIKED to 0.8,
            MusicFlowSource.QUICK_PICKS to 0.5,
            MusicFlowSource.CATALOG to 0.2,
            MusicFlowSource.OFFLINE to 0.4,
            MusicFlowSource.NOW_PLAYING to 0.0,
        )
    }
}

/**
 * Scores and reorders candidates. The returned list is a permutation of [items]; ties keep the
 * earlier input position because the sort is stable on the original index.
 */
fun rankMusicFlowCandidates(
    items: List<MusicFlowItem>,
    config: MusicFlowRankingConfig = MusicFlowRankingConfig(),
    likedArtistKeys: Set<String> = emptySet(),
    lessLikeThisArtistKeys: Set<String> = emptySet(),
    feedTailArtists: List<String> = emptyList(),
): List<MusicFlowItem> {
    if (items.isEmpty()) return emptyList()

    val scored = items.mapIndexed { index, item ->
        val artist = item.primaryArtistKey
        var score = (config.sourceWeights[item.source] ?: 0.0) - index * config.positionDecay
        if (artist != null && artist in likedArtistKeys) score += config.likedArtistBoost
        if (artist != null && artist in lessLikeThisArtistKeys) score -= config.lessLikeThisPenalty
        ScoredCandidate(item, index, score)
    }.sortedWith(
        compareByDescending<ScoredCandidate> { it.score }.thenBy { it.index },
    )

    val picked = ArrayList<MusicFlowItem>(items.size)
    val recentArtists = ArrayDeque<String>()
    feedTailArtists.takeLast(config.artistSpacing).forEach { recentArtists.addLast(it) }

    val remaining = scored.toMutableList()
    while (remaining.isNotEmpty()) {
        val next = remaining.firstOrNull { candidate ->
            val artist = candidate.item.primaryArtistKey
            artist == null || artist !in recentArtists
        } ?: remaining.first()
        remaining.remove(next)
        picked += next.item
        val artist = next.item.primaryArtistKey
        if (artist != null) {
            recentArtists.addLast(artist)
            while (recentArtists.size > config.artistSpacing) recentArtists.removeFirst()
        }
    }
    return picked
}

private data class ScoredCandidate(
    val item: MusicFlowItem,
    val index: Int,
    val score: Double,
)
