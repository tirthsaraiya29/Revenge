package com.revenge.music.utils

import android.content.Context
import android.content.SharedPreferences
import com.revenge.core.models.MediaMetadata
import com.revenge.innertube.models.PODCAST_BROWSE_PREFIX
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.pages.HomePage
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Process-wide podcast listening progress and speed. Entries live in SharedPreferences as one JSON
 * value per episode id, trimmed to the newest [MAX_ENTRIES].
 */
object PodcastProgress {

    const val SKIP_BACK_MS = 15_000L
    const val SKIP_FORWARD_MS = 30_000L

    private const val PREFERENCES_NAME = "podcast_progress"
    private const val SPEED_PREFERENCES_NAME = "podcast_settings"
    private const val KEY_SPEED = "podcast_speed"
    private const val MAX_ENTRIES = 500
    private const val MIN_POSITION_MS = 30_000L
    private const val REMOTE_AHEAD_MS = 60_000L
    private const val MISLABELLED_MIN_MS = 60_000L
    private const val MISLABELLED_MAX_MS = 10 * 60_000L
    private const val EPISODE_MIN_MS = 10 * 60_000L
    private const val COMPLETE_PERCENT = 95

    private val speedValues = listOf(1f, 1.25f, 1.5f, 1.75f, 2f, 0.75f)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val preferences: SharedPreferences
        get() = AppContextHolder.context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val speedPreferences: SharedPreferences
        get() = AppContextHolder.context.getSharedPreferences(SPEED_PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val mutableSpeed = MutableStateFlow(loadSpeed())
    val speed: StateFlow<Float> = mutableSpeed.asStateFlow()

    @Serializable
    data class EpisodeProgress(
        val id: String,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val finished: Boolean = false,
        val updatedAt: Long = 0L,
        val title: String = "",
        val show: String? = null,
        val showBrowseId: String? = null,
        val thumbnailUrl: String? = null,
        val dismissed: Boolean = false,
    ) {
        val fraction: Float?
            get() = when {
                finished -> 1f
                durationMs > 0L -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                else -> null
            }

        val remainingMs: Long
            get() = (durationMs - positionMs).coerceAtLeast(0L)

        val isInProgress: Boolean
            get() = !finished && positionMs > 0L && !dismissed

        fun asPlayed(): EpisodeProgress =
            copy(positionMs = durationMs, finished = true, updatedAt = System.currentTimeMillis())

        val isMislabelled: Boolean
            get() = durationMs in MISLABELLED_MIN_MS until MISLABELLED_MAX_MS &&
                showBrowseId?.startsWith(PODCAST_BROWSE_PREFIX) != true
    }

    fun all(): List<EpisodeProgress> = synchronized(this) { load() }

    fun get(mediaId: String): EpisodeProgress? = synchronized(this) {
        load().firstOrNull { it.id == mediaId }
    }

    val inProgress: List<EpisodeProgress>
        get() = all().filter { it.isInProgress }.sortedByDescending { it.updatedAt }

    fun dismiss(id: String) = updateEntry(id) { it.copy(dismissed = true) }

    fun restore(id: String) = updateEntry(id) { it.copy(dismissed = false) }

    fun update(metadata: MediaMetadata, position: Long, duration: Long) {
        if (metadata.id.isBlank()) return
        synchronized(this) {
            val entries = load().associateBy { it.id }.toMutableMap()
            val existing = entries[metadata.id]
            if (existing == null && position < MIN_POSITION_MS) return

            val finished = isNearEnd(duration, position) ||
                (existing?.finished == true && position < MIN_POSITION_MS)
            val showArtist = metadata.artists.firstOrNull {
                it.id?.startsWith(PODCAST_BROWSE_PREFIX) == true
            }
            entries[metadata.id] = EpisodeProgress(
                id = metadata.id,
                positionMs = position,
                durationMs = duration,
                finished = finished,
                updatedAt = System.currentTimeMillis(),
                title = metadata.title,
                show = showArtist?.name ?: existing?.show,
                showBrowseId = showArtist?.id ?: existing?.showBrowseId,
                thumbnailUrl = metadata.thumbnailUrl ?: existing?.thumbnailUrl,
                dismissed = existing?.dismissed ?: false,
            )
            persist(entries.values)
        }
    }

    fun markFinished(id: String) = updateEntry(id) {
        it.copy(finished = true, positionMs = 0L, updatedAt = System.currentTimeMillis())
    }

    fun isEpisode(metadata: MediaMetadata, durationMs: Long): Boolean =
        metadata.musicVideoType == PODCAST_EPISODE_TYPE &&
            (metadata.artists.any { it.id?.startsWith(PODCAST_BROWSE_PREFIX) == true } ||
                durationMs >= EPISODE_MIN_MS)

    fun isNearEnd(durationMs: Long, positionMs: Long): Boolean {
        if (durationMs <= 0L) return false
        val window = (durationMs / 20L).coerceIn(60_000L, 180_000L)
        return durationMs - positionMs < window
    }

    /** Returns true when the remote progress was applied locally. */
    fun mergeRemote(item: SongItem): Boolean {
        val percent = item.playedPercent ?: return false
        if (percent <= 0) return false
        val durationMs = (item.duration ?: 0).toLong() * 1000L
        val finished = percent >= COMPLETE_PERCENT
        if (!finished && durationMs <= 0L) return false
        val positionMs = durationMs * percent / 100L

        synchronized(this) {
            val entries = load().associateBy { it.id }.toMutableMap()
            val existing = entries[item.id]
            if (existing == null) {
                val showArtist = item.artists.firstOrNull {
                    it.id?.startsWith(PODCAST_BROWSE_PREFIX) == true
                }
                entries[item.id] = EpisodeProgress(
                    id = item.id,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    finished = finished,
                    updatedAt = 0L,
                    title = item.title,
                    show = showArtist?.name,
                    showBrowseId = showArtist?.id,
                    thumbnailUrl = item.thumbnail,
                )
                persist(entries.values)
                return true
            }
            if (existing.finished) return false
            val ahead = positionMs - existing.positionMs > REMOTE_AHEAD_MS
            if (!finished && !ahead) return false
            entries[item.id] = existing.copy(
                positionMs = positionMs,
                durationMs = durationMs,
                finished = finished,
                updatedAt = System.currentTimeMillis(),
            )
            persist(entries.values)
            return true
        }
    }

    fun withoutContinueShelf(
        sections: List<HomePage.Section>,
        title: String,
    ): List<HomePage.Section> = sections.filterNot { section ->
        val episodes = section.items.filterIsInstance<SongItem>()
        if (episodes.isEmpty() || episodes.size != section.items.size) return@filterNot false
        if (episodes.any { !it.isPodcastEpisode }) return@filterNot false
        val titleMatches = section.title.equals(title, ignoreCase = true)
        val allInRange = episodes.all { (it.playedPercent ?: 0) in 1..94 }
        if (!titleMatches && !allInRange) return@filterNot false
        episodes.all { mergeRemote(it) }
    }

    fun cycleSpeed() {
        val current = mutableSpeed.value
        val index = speedValues.indexOfFirst { it == current }
        val next = speedValues[if (index < 0) 0 else (index + 1) % speedValues.size]
        setSpeed(next)
    }

    fun setSpeed(value: Float) {
        val next = if (value in speedValues) value else 1f
        mutableSpeed.value = next
        speedPreferences.edit().putFloat(KEY_SPEED, next).apply()
    }

    fun speedLabel(speed: Float = this.speed.value): String {
        val text = if (speed == speed.toInt().toFloat()) {
            speed.toInt().toString()
        } else {
            String.format(Locale.US, "%.2f", speed).trimEnd('0').trimEnd('.')
        }
        return text + "x"
    }

    private inline fun updateEntry(id: String, transform: (EpisodeProgress) -> EpisodeProgress) {
        synchronized(this) {
            val entries = load().associateBy { it.id }.toMutableMap()
            val existing = entries[id] ?: EpisodeProgress(id = id, updatedAt = System.currentTimeMillis())
            entries[id] = transform(existing)
            persist(entries.values)
        }
    }

    private fun load(): List<EpisodeProgress> {
        val stored = preferences.all
        val entries = ArrayList<EpisodeProgress>(stored.size)
        for ((key, value) in stored) {
            if (value !is String) continue
            val parsed = try {
                json.decodeFromString(EpisodeProgress.serializer(), value)
            } catch (error: Exception) {
                Timber.w(error, "Dropping malformed podcast progress $key")
                null
            } ?: continue
            if (parsed.isMislabelled) continue
            entries += if (!parsed.finished && parsed.positionMs > 0L &&
                isNearEnd(parsed.durationMs, parsed.positionMs)
            ) {
                parsed.copy(finished = true, positionMs = parsed.durationMs)
            } else {
                parsed
            }
        }
        return entries.sortedByDescending { it.updatedAt }
    }

    private fun persist(entries: Collection<EpisodeProgress>) {
        val trimmed = entries.sortedByDescending { it.updatedAt }.take(MAX_ENTRIES)
        val editor = preferences.edit().clear()
        for (entry in trimmed) {
            editor.putString(entry.id, json.encodeToString(EpisodeProgress.serializer(), entry))
        }
        editor.apply()
    }

    private fun loadSpeed(): Float {
        val stored = try {
            AppContextHolder.context
                .getSharedPreferences(SPEED_PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getFloat(KEY_SPEED, 1f)
        } catch (_: Exception) {
            1f
        }
        return if (stored in speedValues) stored else 1f
    }

    private const val PODCAST_EPISODE_TYPE = "MUSIC_VIDEO_TYPE_PODCAST_EPISODE"
}
