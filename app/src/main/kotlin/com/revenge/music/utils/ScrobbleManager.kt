package com.revenge.music.utils

import com.revenge.music.models.MediaMetadata
import com.revenge.music.utils.lastfm.LastFM
import kotlin.math.floor
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Converts player events into Last.fm now-playing and scrobble submissions. Play time accumulates
 * on one-second boundaries and a scrobble fires at most once per track.
 */
class ScrobbleManager(private val scope: CoroutineScope) {

  var minSongDuration: Int = 30
  var scrobbleDelayPercent: Float = 0.5f
  var scrobbleDelaySeconds: Int = 50
  var useNowPlaying: Boolean = true
  var enableScrobbling: Boolean = false
  var useSendLikes: Boolean = false

  internal var nowSeconds: () -> Long = { System.currentTimeMillis() / 1000L }

  internal var submitNowPlaying: suspend (
    artist: String,
    track: String,
    album: String?,
    duration: Int,
  ) -> Unit = { artist, track, album, duration ->
    LastFM.updateNowPlaying(artist, track, album, null, duration)
  }

  internal var submitScrobble: suspend (
    artist: String,
    track: String,
    timestamp: Long,
    album: String?,
    duration: Int,
  ) -> Unit = { artist, track, timestamp, album, duration ->
    LastFM.scrobble(artist, track, timestamp, album, null, duration)
  }

  private var currentId: String? = null
  private var currentDuration: Int = 0
  private var currentArtist: String = "Unknown Artist"
  private var currentTitle: String = ""
  private var currentAlbum: String? = null
  private var playTimeSeconds: Int = 0
  private var scrobbled: Boolean = false
  private var playing: Boolean = false
  private var ticker: Job? = null
  private var nowPlayingJob: Job? = null
  private var scrobbleJob: Job? = null

  @Volatile private var destroyed = false

  fun destroy() {
    destroyed = true
    ticker?.cancel()
    ticker = null
    nowPlayingJob?.cancel()
    nowPlayingJob = null
    scrobbleJob?.cancel()
    scrobbleJob = null
    playing = false
  }

  fun onSongStart(metadata: MediaMetadata?, duration: Long? = null) {
    if (destroyed) return
    val id = metadata?.id
    val newTrack = id != null && id != currentId
    if (newTrack) {
      currentId = id
      playTimeSeconds = 0
      scrobbled = false
    }
    if (metadata != null) {
      currentTitle = metadata.title
      currentArtist = metadata.artists.joinToString(", ") { it.name }.ifBlank { "Unknown Artist" }
      currentAlbum = metadata.album?.title
      currentDuration =
        duration?.takeIf { it > 0 }?.toInt() ?: metadata.duration.takeIf { it > 0 } ?: 0
    }
    if (newTrack) submitNowPlayingIfEnabled()
    startTicker()
  }

  fun onSongResume(metadata: MediaMetadata) {
    if (destroyed) return
    playing = true
    if (metadata.id != currentId) {
      onSongStart(metadata)
    } else {
      startTicker()
    }
  }

  fun onSongPause() {
    playing = false
    ticker?.cancel()
    ticker = null
  }

  fun onPlayerStateChanged(isPlaying: Boolean, metadata: MediaMetadata?, duration: Long? = null) {
    if (destroyed) return
    if (metadata != null && metadata.id != currentId) {
      onSongStart(metadata, duration)
    } else if (duration != null && duration > 0 && metadata == null) {
      currentDuration = duration.toInt()
    }
    playing = isPlaying
    if (isPlaying) startTicker() else onSongPause()
  }

  fun onSongStop() {
    playing = false
    ticker?.cancel()
    ticker = null
  }

  // ------------------------------------------------------------------ internals

  private fun submitNowPlayingIfEnabled() {
    if (destroyed) return
    if (!enableScrobbling || !useNowPlaying || !LastFM.isInitialized()) return
    val artist = currentArtist
    val title = currentTitle
    val album = currentAlbum
    val duration = currentDuration
    nowPlayingJob?.cancel()
    nowPlayingJob =
      scope.launch {
        runCatching { submitNowPlaying(artist, title, album, duration) }
          .onFailure { Timber.tag(TAG).w(it, "now-playing failed") }
      }
  }

  private fun startTicker() {
    if (destroyed || ticker?.isActive == true) return
    if (!playing) return
    ticker =
      scope.launch {
        while (isActive && !destroyed && playing && !scrobbled) {
          delay(1_000)
          if (destroyed || !playing || scrobbled) break
          playTimeSeconds += 1
          maybeScrobble()
        }
      }
  }

  private fun maybeScrobble() {
    if (scrobbled || destroyed) return
    val duration = currentDuration
    if (duration > 0 && duration < minSongDuration) return
    val percentSeconds =
      if (duration > 0) floor(duration * scrobbleDelayPercent.toDouble()).toInt() else 300
    val threshold = min(percentSeconds, scrobbleDelaySeconds)
    if (playTimeSeconds < threshold) return

    scrobbled = true
    val timestamp = nowSeconds() - playTimeSeconds
    val artist = currentArtist
    val title = currentTitle
    val album = currentAlbum
    ticker?.cancel()
    ticker = null
    if (!enableScrobbling || !LastFM.isInitialized()) return
    scrobbleJob =
      scope.launch {
        runCatching { submitScrobble(artist, title, timestamp, album, duration) }
          .onFailure { Timber.tag(TAG).w(it, "scrobble failed") }
      }
  }

  private companion object {
    const val TAG = "ScrobbleManager"
  }
}
