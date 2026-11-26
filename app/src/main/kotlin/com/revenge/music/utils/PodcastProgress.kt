package com.revenge.music.utils

import android.content.Context
import android.content.SharedPreferences
import com.revenge.innertube.models.PODCAST_BROWSE_PREFIX
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.pages.HomePage
import com.revenge.music.models.MediaMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TEN_MINUTES_MS = 10L * 60 * 1000

@Serializable
data class EpisodeProgress(
    val id: String,
    val positionMs: Long,
    val durationMs: Long,
    val finished: Boolean,
    val updatedAt: Long,
    val title: String = "",
    val show: String? = null,
    val showBrowseId: String? = null,
    val thumbnailUrl: String? = null,
    val dismissed: Boolean = false,
) {
    val fraction: Float?
        get() = when {
            finished -> 1f
            durationMs > 0 -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
            else -> null
        }

    val remainingMs: Long get() = (durationMs - positionMs).coerceAtLeast(0)

    val isInProgress: Boolean get() = !finished && positionMs > 0 && !dismissed

    fun asPlayed(): EpisodeProgress = copy(dismissed = true)

    val isMislabelled: Boolean
        get() = durationMs in 1 until TEN_MINUTES_MS &&
            showBrowseId?.startsWith(PODCAST_BROWSE_PREFIX) != true
}

class PodcastProgress private constructor(
    private val progress: SharedPreferences,
    private val settings: SharedPreferences,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val lock = Any()

    private val _speed = MutableStateFlow(settings.getFloat(SPEED_KEY, 1f))
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _entries = MutableStateFlow(allLocked().associateBy { it.id })
    val entries: StateFlow<Map<String, EpisodeProgress>> = _entries.asStateFlow()

    fun get(mediaId: String): EpisodeProgress? = synchronized(lock) {
        val raw = progress.getString(mediaId, null) ?: return null
        runCatching {
            json.decodeFromString(EpisodeProgress.serializer(), raw)
        }.getOrNull()
    }

    fun inProgress(): List<EpisodeProgress> = synchronized(lock) {
        inProgress(_entries.value)
    }

    fun inProgress(entries: Map<String, EpisodeProgress>): List<EpisodeProgress> =
        entries.values
            .filter { it.isInProgress }
            .sortedByDescending { it.updatedAt }

    fun update(metadata: MediaMetadata, positionMs: Long, durationMs: Long) {
        synchronized(lock) {
            val previous = getLocked(metadata.id)
            if (previous == null && positionMs < MIN_TRACKING_POSITION_MS) return
            val nearEnd = isNearEnd(positionMs, durationMs)
            val finished = nearEnd ||
                (previous?.finished == true && positionMs < MIN_TRACKING_POSITION_MS)
            val entry = EpisodeProgress(
                id = metadata.id,
                positionMs = positionMs.coerceAtLeast(0),
                durationMs = durationMs.coerceAtLeast(0),
                finished = finished,
                updatedAt = System.currentTimeMillis(),
                title = metadata.title,
                show = metadata.artists.firstOrNull()?.name,
                showBrowseId = metadata.artists.firstOrNull()?.id,
                thumbnailUrl = metadata.thumbnailUrl,
                dismissed = previous?.dismissed ?: false,
            )
            persistLocked(entry)
        }
    }

    fun markFinished(id: String) {
        synchronized(lock) {
            val previous = getLocked(id) ?: EpisodeProgress(
                id = id,
                positionMs = 0,
                durationMs = 0,
                finished = false,
                updatedAt = 0,
                title = "",
            )
            persistLocked(
                previous.copy(
                    finished = true,
                    positionMs = 0,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun dismiss(id: String) {
        synchronized(lock) {
            getLocked(id)?.let { persistLocked(it.copy(dismissed = true)) }
        }
    }

    fun restore(id: String) {
        synchronized(lock) {
            getLocked(id)?.let { persistLocked(it.copy(dismissed = false)) }
        }
    }

    fun mergeRemote(song: SongItem): Boolean {
        val percent = song.playedPercent ?: return false
        if (percent <= 0) return false
        val durationMs = (song.duration ?: 0).toLong() * 1000
        val finished = percent >= REMOTE_PLAYED_PERCENT
        if (!finished && durationMs <= 0) return false
        val position = if (durationMs > 0) durationMs * percent / 100 else 0
        synchronized(lock) {
            val existing = getLocked(song.id)
            if (existing == null) {
                persistLocked(
                    EpisodeProgress(
                        id = song.id,
                        positionMs = position,
                        durationMs = durationMs,
                        finished = finished,
                        updatedAt = 0,
                        title = song.title,
                        show = song.artists.firstOrNull()?.name,
                        showBrowseId = song.artists.firstOrNull()?.id,
                        thumbnailUrl = song.thumbnail,
                    ),
                )
                return true
            }
            if (existing.finished) return false
            val ahead = position - existing.positionMs > REMOTE_AHEAD_MS
            if (!finished && !ahead) return false
            persistLocked(
                existing.copy(
                    positionMs = position,
                    durationMs = if (durationMs > 0) durationMs else existing.durationMs,
                    finished = finished,
                    updatedAt = existing.updatedAt,
                    title = song.title.ifBlank { existing.title },
                    show = song.artists.firstOrNull()?.name ?: existing.show,
                    showBrowseId = song.artists.firstOrNull()?.id ?: existing.showBrowseId,
                    thumbnailUrl = song.thumbnail.ifBlank { existing.thumbnailUrl.orEmpty() }
                        .ifBlank { null },
                ),
            )
            return true
        }
    }

    fun withoutContinueShelf(
        sections: List<HomePage.Section>,
        title: String,
    ): List<HomePage.Section> = synchronized(lock) {
        val wanted = title.lowercase()
        sections.filterNot { section ->
            val songs = section.items.filterIsInstance<SongItem>()
            if (songs.isEmpty() || !songs.all { it.isPodcastEpisode }) return@filterNot false
            val titleMatches = section.title?.lowercase() == wanted
            val allInProgress = songs.all { song ->
                (song.playedPercent ?: 0) in 1..94
            }
            if (!titleMatches && !allInProgress) return@filterNot false
            songs.all { mergeRemote(it) }
        }
    }

    fun isEpisode(metadata: MediaMetadata, durationMs: Long): Boolean {
        if (metadata.musicVideoType != "MUSIC_VIDEO_TYPE_PODCAST_EPISODE") return false
        val hasShowPrefix = metadata.artists.any { it.id?.startsWith(PODCAST_BROWSE_PREFIX) == true }
        return hasShowPrefix || durationMs >= TEN_MINUTES_MS
    }

    fun cycleSpeed() {
        val current = _speed.value
        val index = SPEEDS.indexOfFirst { it == current }
        val next = SPEEDS[(index + 1).mod(SPEEDS.size)]
        _speed.value = next
        settings.edit().putFloat(SPEED_KEY, next).apply()
    }

    fun speedLabel(speed: Float = _speed.value): String {
        val base = if (speed == speed.toInt().toFloat()) {
            speed.toInt().toString()
        } else {
            speed.toString().trimEnd('0').trimEnd('.')
        }
        return "${base}x"
    }

    private fun getLocked(id: String): EpisodeProgress? {
        val raw = progress.getString(id, null) ?: return null
        return runCatching {
            json.decodeFromString(EpisodeProgress.serializer(), raw)
        }.getOrNull()
    }

    private fun allLocked(): List<EpisodeProgress> = progress.all.values.mapNotNull { raw ->
        (raw as? String)?.let {
            runCatching { json.decodeFromString(EpisodeProgress.serializer(), it) }.getOrNull()
        }
    }.filterNot { it.isMislabelled }

    private fun persistLocked(entry: EpisodeProgress) {
        val editor = progress.edit()
        editor.putString(entry.id, json.encodeToString(EpisodeProgress.serializer(), entry))
        val all = allLocked() + entry
        if (all.size > MAX_ENTRIES) {
            all.sortedByDescending { it.updatedAt }
                .drop(MAX_ENTRIES)
                .forEach { editor.remove(it.id) }
        }
        editor.apply()
        _entries.value = allLocked().associateBy { it.id }
    }

    companion object {
        const val SKIP_BACK_MS = 15_000L
        const val SKIP_FORWARD_MS = 30_000L
        const val MAX_ENTRIES = 500
        const val REMOTE_PLAYED_PERCENT = 95
        private const val MIN_TRACKING_POSITION_MS = 30_000L
        private const val REMOTE_AHEAD_MS = 60_000L
        private const val SPEED_KEY = "podcast_speed"
        private val SPEEDS = listOf(1f, 1.25f, 1.5f, 1.75f, 2f, 0.75f)

        @Volatile private var instance: PodcastProgress? = null

        fun getInstance(context: Context): PodcastProgress =
            instance ?: synchronized(this) {
                instance ?: PodcastProgress(
                    context.getSharedPreferences("podcast_progress", Context.MODE_PRIVATE),
                    context.getSharedPreferences("podcast_settings", Context.MODE_PRIVATE),
                ).also {
                    it.migrateLegacy()
                    instance = it
                }
            }

        fun isNearEnd(positionMs: Long, durationMs: Long): Boolean {
            if (durationMs <= 0) return false
            val window = (durationMs / 20).coerceIn(60_000L, 180_000L)
            return durationMs - positionMs < window
        }
    }

    private fun migrateLegacy() {
        synchronized(lock) {
            val entries = allLocked()
            for (entry in entries) {
                if (entry.isMislabelled) {
                    progress.edit().remove(entry.id).apply()
                } else if (!entry.finished && isNearEnd(entry.positionMs, entry.durationMs)) {
                    persistLocked(entry.copy(finished = true))
                }
            }
            _entries.value = allLocked().associateBy { it.id }
        }
    }
}
