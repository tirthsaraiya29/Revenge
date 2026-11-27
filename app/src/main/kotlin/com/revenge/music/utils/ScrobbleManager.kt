package com.revenge.music.utils

import com.revenge.core.models.MediaMetadata
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.floor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Playback-driven now-playing and scrobble submission. Time is accumulated in one-second steps
 * from [onPlayerStateChanged] while the player is playing; a track is scrobbled once when the
 * played time reaches its threshold.
 */
class ScrobbleManager(private val scope: CoroutineScope) {

    var minSongDuration: Int = DEFAULT_MIN_SONG_DURATION
    var scrobbleDelayPercent: Float = DEFAULT_DELAY_PERCENT
    var scrobbleDelaySeconds: Int = DEFAULT_DELAY_SECONDS
    var useNowPlaying: Boolean = true
    var enableScrobbling: Boolean = false
    var useSendLikes: Boolean = false

    private var ticker: Job? = null
    private var currentId: String? = null
    private var playedSeconds: Int = 0
    private var scrobbled: Boolean = false
    private var playing: Boolean = false
    private var destroyed: AtomicBoolean = AtomicBoolean(false)

    fun destroy() {
        destroyed.set(true)
        ticker?.cancel()
        ticker = null
    }

    fun onSongStart(metadata: MediaMetadata?, duration: Long? = null) {
        if (metadata == null) {
            onSongStop()
            return
        }
        if (metadata.id == currentId) {
            // Same track: keep the accumulated time, only (re)start ticking.
            startTicker()
            return
        }
        currentId = metadata.id
        playedSeconds = 0
        scrobbled = false
        submitNowPlaying(metadata, duration)
        startTicker()
    }

    fun onSongResume(metadata: MediaMetadata) {
        playing = true
        if (metadata.id != currentId) {
            currentId = metadata.id
            playedSeconds = 0
            scrobbled = false
            submitNowPlaying(metadata, null)
        }
        startTicker()
    }

    fun onSongPause() {
        playing = false
    }

    fun onPlayerStateChanged(isPlaying: Boolean, metadata: MediaMetadata?, duration: Long? = null) {
        if (metadata == null) {
            if (!isPlaying) onSongPause() else onSongStop()
            return
        }
        if (isPlaying) {
            if (metadata.id != currentId) {
                onSongStart(metadata, duration)
            }
            playing = true
            startTicker()
        } else {
            onSongPause()
        }
    }

    fun onSongStop() {
        playing = false
        ticker?.cancel()
        ticker = null
        currentId = null
        playedSeconds = 0
        scrobbled = false
    }

    private fun startTicker() {
        if (destroyed.get() || ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                delay(1_000L)
                if (destroyed.get()) return@launch
                if (!playing || scrobbled) continue
                playedSeconds += 1
                val metadata = lastMetadata
                if (metadata != null) maybeScrobble(metadata)
            }
        }
    }

    private var lastMetadata: MediaMetadata? = null
    private var lastDurationMs: Long? = null

    private fun submitNowPlaying(metadata: MediaMetadata, duration: Long?) {
        lastMetadata = metadata
        lastDurationMs = duration
        if (!enableScrobbling || !useNowPlaying || !LastFM.isInitialized()) return
        val artist = metadata.artists.joinToString(", ") { it.name }.ifBlank { UNKNOWN_ARTIST }
        scope.launch {
            runCatching {
                LastFM.updateNowPlaying(
                    artist = artist,
                    track = metadata.title,
                    album = metadata.album?.title,
                    duration = durationSeconds(metadata, duration),
                )
            }.onFailure { Timber.w(it, "Last.fm now-playing failed") }
        }
    }

    private fun maybeScrobble(metadata: MediaMetadata) {
        if (!enableScrobbling || scrobbled || !LastFM.isInitialized()) return
        val seconds = durationSeconds(metadata, lastDurationMs)
        if (seconds in 1 until minSongDuration) return

        val percentDuration = if (seconds > 0) floor(seconds * scrobbleDelayPercent).toInt() else floor(300f * scrobbleDelayPercent).toInt()
        val threshold = minOf(percentDuration, scrobbleDelaySeconds)
        if (playedSeconds < threshold) return

        scrobbled = true
        val artist = metadata.artists.joinToString(", ") { it.name }.ifBlank { UNKNOWN_ARTIST }
        val timestamp = System.currentTimeMillis() / 1000L - playedSeconds
        scope.launch {
            runCatching {
                LastFM.scrobble(
                    artist = artist,
                    track = metadata.title,
                    timestamp = timestamp,
                    album = metadata.album?.title,
                    duration = seconds.takeIf { it > 0 },
                )
            }.onFailure { Timber.w(it, "Last.fm scrobble failed") }
        }
    }

    private fun durationSeconds(metadata: MediaMetadata, durationMs: Long?): Int {
        val millis = durationMs ?: (metadata.duration.toLong() * 1000L)
        return (millis / 1000L).toInt().coerceAtLeast(0)
    }

    companion object {
        const val DEFAULT_MIN_SONG_DURATION = 30
        const val DEFAULT_DELAY_PERCENT = 0.5f
        const val DEFAULT_DELAY_SECONDS = 50
        private const val UNKNOWN_ARTIST = "Unknown Artist"
    }
}
