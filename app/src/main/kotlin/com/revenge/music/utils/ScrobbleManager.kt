package com.revenge.music.utils

import com.revenge.music.models.MediaMetadata
import com.revenge.music.utils.lastfm.LastFM
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Tracks listening time and submits Last.fm scrobbles. Network failures are logged and swallowed.
 */
class ScrobbleManager(
    private val scope: CoroutineScope,
    private val minSongDuration: Int = 30,
    private val scrobbleDelayPercent: Double = 0.5,
    private val scrobbleDelaySeconds: Int = 50,
) {
    var useNowPlaying: Boolean = false
    var enableScrobbling: Boolean = false
    var useSendLikes: Boolean = false

    private var current: MediaMetadata? = null
    private var currentDurationSeconds: Int = 0
    private var playedSeconds: Int = 0
    private var scrobbled: Boolean = false
    private var timerJob: Job? = null

    fun onSongStart(metadata: MediaMetadata) {
        current = metadata
        currentDurationSeconds = metadata.duration.coerceAtLeast(0)
        playedSeconds = 0
        scrobbled = false
        if (metadata.liked && useSendLikes) {
            scope.launch {
                runCatching {
                    LastFM.setLoveStatus(artistOf(metadata), metadata.title, love = true)
                }.onFailure { Timber.w(it, "Last.fm love failed") }
            }
        }
        if (useNowPlaying) {
            scope.launch {
                runCatching {
                    LastFM.updateNowPlaying(
                        artist = artistOf(metadata),
                        track = metadata.title,
                        album = metadata.album?.title,
                        duration = metadata.duration.takeIf { it > 0 },
                    )
                }.onFailure { Timber.w(it, "Last.fm now playing failed") }
            }
        }
    }

    fun onTrackChange(metadata: MediaMetadata) = onSongStart(metadata)

    fun onPlaybackStateChanged(isPlaying: Boolean) {
        if (isPlaying) startTimer() else stopTimer()
    }

    fun onPlayingChanged(isPlaying: Boolean) = onPlaybackStateChanged(isPlaying)

    fun onPause() = stopTimer()

    fun destroy() {
        stopTimer()
        current = null
    }

    private fun startTimer() {
        if (timerJob?.isActive == true) return
        timerJob = scope.launch {
            while (true) {
                delay(1_000)
                val metadata = current ?: continue
                playedSeconds += 1
                maybeScrobble(metadata)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun maybeScrobble(metadata: MediaMetadata) {
        if (scrobbled || !enableScrobbling) return
        if (currentDurationSeconds > 0 && currentDurationSeconds < minSongDuration) return
        val threshold = min(
            (scrobbleDelayPercent * currentDurationSeconds).toInt(),
            scrobbleDelaySeconds,
        ).coerceAtLeast(1)
        if (playedSeconds < threshold) return
        scrobbled = true
        val timestamp = System.currentTimeMillis() / 1000 - playedSeconds
        scope.launch {
            runCatching {
                LastFM.scrobble(
                    artist = artistOf(metadata),
                    track = metadata.title,
                    timestamp = timestamp,
                    album = metadata.album?.title,
                    duration = metadata.duration.takeIf { it > 0 },
                )
            }.onFailure { Timber.w(it, "Last.fm scrobble failed") }
        }
    }

    private fun artistOf(metadata: MediaMetadata): String =
        metadata.artists.joinToString(", ") { it.name }
}
