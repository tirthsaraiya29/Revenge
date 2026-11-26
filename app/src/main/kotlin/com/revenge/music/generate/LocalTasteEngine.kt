package com.revenge.music.generate

import com.revenge.innertube.YouTube
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.models.WatchEndpoint
import com.revenge.music.db.MusicDatabase
import com.revenge.music.db.entities.PlaylistEntity
import com.revenge.music.db.entities.PlaylistSongMap
import com.revenge.music.db.entities.SongPlayStatsEntity
import com.revenge.music.models.MediaMetadata
import java.util.Calendar
import javax.inject.Inject
import kotlin.math.ln
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber

const val MAX_SUGGESTIONS = 25
const val PLAY_WEIGHT = 2.0
const val SKIP_PENALTY = 3.0
const val LIKED_BONUS = 5.0
const val RECENCY_BONUS = 1.5
const val TIME_OF_DAY_BONUS = 1.3
const val SIMILAR_TRACK_BONUS = 2.0
const val SIMILAR_ARTIST_BONUS = 1.0
const val TAG_MATCH_BONUS = 1.5
const val GENRE_MATCH_BONUS = 1.0

private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
private const val MONTH_MS = 30L * 24 * 60 * 60 * 1000

fun trackKeyOf(title: String, artist: String): String =
    "${title.trim()}|${artist.trim()}".lowercase()

fun currentTimeOfDay(hour: Int): String = when (hour) {
    in 6..11 -> "morning"
    in 12..17 -> "afternoon"
    in 18..21 -> "evening"
    else -> "night"
}

fun applyCandidateFilter(stats: SongPlayStatsEntity): Boolean =
    stats.skipCount < 2 || stats.totalPlayTimeMs > 45_000L

fun scoreStats(
    stats: SongPlayStatsEntity,
    likedKeys: Set<String>,
    timeOfDay: String,
    now: Long = System.currentTimeMillis(),
): Double {
    val recent = stats.lastPlayedAtMillis > 0L && now - stats.lastPlayedAtMillis <= WEEK_MS
    val liked = stats.trackKey in likedKeys
    var score = ln(1.0 + stats.totalPlayTimeMs.coerceAtLeast(0L) / 60_000.0) * PLAY_WEIGHT -
        stats.skipCount * SKIP_PENALTY +
        if (liked) LIKED_BONUS else 0.0 +
        if (recent) RECENCY_BONUS else 0.0
    if ((timeOfDay == "morning" && recent) || (timeOfDay == "night" && liked)) {
        score *= TIME_OF_DAY_BONUS
    }
    return score.coerceAtLeast(0.0)
}

fun scoreCandidateWithExternals(
    baseScore: Double,
    similarTrack: Boolean = false,
    similarArtist: Boolean = false,
    tagMatch: Boolean = false,
    genreMatch: Boolean = false,
): Double {
    var score = baseScore
    if (similarTrack) score += SIMILAR_TRACK_BONUS
    if (similarArtist) score += SIMILAR_ARTIST_BONUS
    if (tagMatch) score += TAG_MATCH_BONUS
    if (genreMatch) score += GENRE_MATCH_BONUS
    return score.coerceAtLeast(0.0)
}

open class LocalTasteEngine @Inject constructor(
    protected val database: MusicDatabase,
) {
    open suspend fun generate(count: Int = MAX_SUGGESTIONS): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val exclusions = database.recommendationExclusionDao.getAll()
                    .map { it.trackKey }
                    .toSet()
                val timeOfDay = currentTimeOfDay(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
                val now = System.currentTimeMillis()

                val seeds = loadSeeds(now) ?: return@withContext Result.failure(
                    IllegalStateException("No listening history available yet"),
                )
                val likedKeys = likedTrackKeys()
                val eligible = seeds
                    .filter { it.trackKey !in exclusions && applyCandidateFilter(it) }
                    .sortedByDescending { scoreStats(it, likedKeys, timeOfDay, now) }
                if (eligible.isEmpty()) {
                    return@withContext Result.failure(
                        IllegalStateException("All candidate tracks have been excluded"),
                    )
                }

                val expanded = expandSeeds(eligible.take(5), exclusions)
                val songs = if (expanded.size >= 5) {
                    expanded.take(count)
                } else {
                    val merged = (expanded + eligible.map { it.toLocalSong() })
                        .distinctBy { it.id }
                    merged.take(count)
                }
                if (songs.isEmpty()) {
                    return@withContext Result.failure(
                        IllegalStateException("Could not generate playlist from seeds"),
                    )
                }

                persistPlaylist(DAILY_MIX_ID, "My Taste Mix", songs.map { it.toMediaMetadata() })
                Result.success(DAILY_MIX_ID)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                Timber.e(t, "Local taste generation failed")
                Result.failure(t)
            }
        }

    protected suspend fun loadSeeds(now: Long): List<SongPlayStatsEntity>? {
        val recent = database.songPlayStatsDao.mostPlayedSince(now - MONTH_MS, 100)
        if (recent.isNotEmpty()) return recent
        val played = database.songPlayStatsDao.recentlyPlayed(20)
        return played.ifEmpty { null }
    }

    protected suspend fun likedTrackKeys(): Set<String> {
        val liked = database.likedSongsByRowIdAsc().first()
        return liked.map { song ->
            trackKeyOf(song.song.title, song.artists.firstOrNull()?.name.orEmpty())
        }.toSet()
    }

    protected suspend fun expandSeeds(
        seeds: List<SongPlayStatsEntity>,
        exclusions: Set<String>,
    ): List<SongItem> {
        val result = LinkedHashMap<String, SongItem>()
        for (seed in seeds) {
            val videoId = seed.videoId?.takeIf { it.isNotBlank() } ?: continue
            val endpoint = WatchEndpoint(videoId = videoId)
            val next = YouTube.next(endpoint).getOrNull() ?: continue
            collect(next.items, exclusions, result)
            next.relatedEndpoint?.let { related ->
                YouTube.related(related).getOrNull()?.songs?.let {
                    collect(it, exclusions, result)
                }
            }
        }
        return result.values.toList()
    }

    protected fun collect(
        items: List<SongItem>,
        exclusions: Set<String>,
        into: MutableMap<String, SongItem>,
    ) {
        for (item in items) {
            val key = trackKeyOf(item.title, item.artists.joinToString(", ") { it.name })
            if (key in exclusions) continue
            if (into.containsKey(key)) continue
            into[key] = item
        }
    }

    protected suspend fun persistPlaylist(
        id: String,
        name: String,
        songs: List<MediaMetadata>,
    ) {
        database.withTransaction {
            database.clearPlaylist(id)
            database.insert(PlaylistEntity(id = id, name = name, isLocal = true, isEditable = true))
            songs.forEachIndexed { index, metadata ->
                database.insert(metadata)
                database.insert(
                    PlaylistSongMap(playlistId = id, songId = metadata.id, position = index),
                )
            }
        }
    }

    protected fun SongPlayStatsEntity.toLocalSong(): SongItem = SongItem(
        id = videoId?.takeIf { it.isNotBlank() } ?: "local_${trackKey.hashCode()}",
        title = title.ifBlank { "Unknown" },
        artists = listOf(com.revenge.innertube.models.Artist(artist.ifBlank { "Unknown" }, null)),
        duration = null,
        thumbnail = artworkUrl.orEmpty(),
    )

    companion object {
        const val DAILY_MIX_ID = "local_taste_daily_mix"
        const val DAILY_MIX_NAME = "My Taste Mix"
    }
}

internal fun SongItem.toMediaMetadata(): MediaMetadata = MediaMetadata(
    id = id,
    title = title,
    artists = artists.map { MediaMetadata.Artist(it.id, it.name) },
    duration = duration ?: -1,
    thumbnailUrl = thumbnail,
    album = album?.let { MediaMetadata.Album(it.id, it.name) },
    musicVideoType = musicVideoType,
    explicit = explicit,
)
