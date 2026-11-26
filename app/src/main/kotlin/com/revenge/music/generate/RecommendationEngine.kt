package com.revenge.music.generate

import com.revenge.innertube.YouTube
import com.revenge.innertube.models.SongItem
import com.revenge.music.db.MusicDatabase
import com.revenge.music.utils.lastfm.LastFmTasteApi
import java.util.Calendar
import javax.inject.Inject
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

class RecommendationEngine @Inject constructor(
    database: MusicDatabase,
    private val genresRepository: GenresRepository,
    private val tasteProfileProvider: TasteProfileProvider,
    private val lastFmTasteApi: LastFmTasteApi,
) : LocalTasteEngine(database) {

    @Volatile
    var allowNullDatabaseForTesting: Boolean = false

    override suspend fun generate(count: Int): Result<String> = withContext(Dispatchers.IO) {
        try {
            generateRecommended(count) ?: super.generate(count)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            Timber.e(t, "Recommendation generation failed; falling back to local taste")
            super.generate(count)
        }
    }

    private suspend fun generateRecommended(count: Int): Result<String>? {
        val exclusions = database.recommendationExclusionDao.getAll().map { it.trackKey }.toSet()
        val timeOfDay = currentTimeOfDay(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
        val now = System.currentTimeMillis()
        val seeds = loadSeeds(now) ?: return Result.failure(
            IllegalStateException("No listening history available yet"),
        )
        val likedKeys = likedTrackKeys()
        val eligible = seeds
            .filter { it.trackKey !in exclusions && applyCandidateFilter(it) }
            .sortedByDescending { scoreStats(it, likedKeys, timeOfDay, now) }
        if (eligible.isEmpty()) {
            return Result.failure(IllegalStateException("All candidate tracks have been excluded"))
        }

        val profile = tasteProfileProvider.get()
        val tasteGenres = (
            profile?.topGenres.orEmpty() +
                runCatching { genresRepository.topGenres(15) }.getOrDefault(emptyList())
            ).map { it.lowercase() }.distinct().toSet()

        val similarTracks = LinkedHashSet<String>()
        val similarArtists = LinkedHashSet<String>()
        val topSeeds = eligible.take(3)
        coroutineScope {
            topSeeds.map { seed ->
                async {
                    val tracks = lastFmTasteApi.getSimilarTracks(seed.artist, seed.title)
                        .getOrNull().orEmpty()
                    val artists = lastFmTasteApi.getSimilarArtists(seed.artist)
                        .getOrNull().orEmpty()
                    tracks to artists
                }
            }.awaitAll()
        }.forEach { (tracks, artists) ->
            tracks.forEach {
                similarTracks += trackKeyOf(it.name, it.artist)
            }
            artists.forEach {
                similarArtists += it.name.trim().lowercase()
            }
        }

        val candidates = LinkedHashMap<String, SongItem>()
        for (seed in topSeeds) {
            val similar = lastFmTasteApi.getSimilarTracks(seed.artist, seed.title)
                .getOrNull().orEmpty().take(5)
            for (track in similar) {
                val key = trackKeyOf(track.name, track.artist)
                if (key in exclusions) continue
                val found = YouTube.search(
                    "${track.artist} ${track.name}",
                    YouTube.SearchFilter.FILTER_SONG,
                ).getOrNull()?.items?.filterIsInstance<SongItem>()?.firstOrNull() ?: continue
                candidates.putIfAbsent(key, found)
            }
        }

        val expanded = expandSeeds(eligible.take(5), exclusions)
        val merged = LinkedHashMap<String, SongItem>()
        for (item in candidates.values + expanded) {
            val key = trackKeyOf(item.title, item.artists.joinToString(", ") { it.name })
            if (key in exclusions) continue
            merged.putIfAbsent(item.id, item)
        }
        val capped = merged.values.take(max(30, count * 3))
        if (capped.isEmpty()) return null

        val statsByKey = runCatching {
            database.songPlayStatsDao.findByKeys(
                capped.map { trackKeyOf(it.title, it.artists.joinToString(", ") { a -> a.name }) },
            ).associateBy { it.trackKey }
        }.getOrDefault(emptyMap())

        val artistGenres = resolveArtistGenres(capped)
        val scored = capped.map { item ->
            val leadArtist = item.artists.firstOrNull()
            val artistName = leadArtist?.name.orEmpty()
            val artistKey = leadArtist?.id?.takeIf { it.isNotBlank() } ?: artistName.lowercase()
            val key = trackKeyOf(item.title, item.artists.joinToString(", ") { it.name })
            val stats = statsByKey[key]
            val base = stats?.let { scoreStats(it, likedKeys, timeOfDay, now) } ?: 5.0
            val genres = artistGenres[artistName.lowercase()].orEmpty()
            val tagMatch = genres.any { it in (profile?.topGenres.orEmpty()) }
            val genreMatch = genres.any { it in tasteGenres } ||
                tasteGenres.any { genreMatches(genre = it, item = item) }
            val score = scoreCandidateWithExternals(
                baseScore = base,
                similarTrack = key in similarTracks,
                similarArtist = artistKey.lowercase() in similarArtists ||
                    artistName.lowercase() in similarArtists,
                tagMatch = tagMatch,
                genreMatch = genreMatch,
            )
            item to score
        }.sortedByDescending { it.second }

        val songs = if (scored.size >= 5) {
            scored.take(count).map { it.first }
        } else {
            (scored.map { it.first } + eligible.map { it.toLocalSong() })
                .distinctBy { it.id }
                .take(count)
        }
        if (songs.isEmpty()) return null

        val playlistId = "local_taste_${System.currentTimeMillis()}"
        val playlistName = "Taste Mix (${timeOfDay.replaceFirstChar { it.uppercase() }})"
        persistPlaylist(playlistId, playlistName, songs.map { it.toMediaMetadata() })
        return Result.success(playlistId)
    }

    private suspend fun resolveArtistGenres(items: List<SongItem>): Map<String, List<String>> {
        val artists = items.mapNotNull { it.artists.firstOrNull()?.name }
            .filter { it.isNotBlank() }
            .distinct()
        val pairs = coroutineScope {
            artists.map { artist ->
                async {
                    val genres = withTimeoutOrNull(2_000) {
                        genresRepository.genresForTrack("unknown|$artist")
                    }.orEmpty()
                    artist.lowercase() to genres
                }
            }.awaitAll()
        }
        return pairs.toMap()
    }

    private fun genreMatches(genre: String, item: SongItem): Boolean {
        if (genre.isBlank()) return false
        val pattern = Regex("\\b${Regex.escape(genre)}\\b", RegexOption.IGNORE_CASE)
        val haystack = buildString {
            append(item.title)
            append(' ')
            append(item.artists.joinToString(" ") { it.name })
        }
        return pattern.containsMatchIn(haystack)
    }
}
