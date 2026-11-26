package com.revenge.music.playback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pauses playback after a countdown, or at the end of the current song, fading out first so the cut
 * is not abrupt.
 */
class SleepTimer(
  private val scope: CoroutineScope,
  var player: Player,
) : Player.Listener {
  private var timerJob: Job? = null

  var triggerTime by mutableLongStateOf(-1L)
    private set

  var pauseWhenSongEnd by mutableStateOf(false)
    private set

  val isActive: Boolean
    get() = triggerTime != -1L || pauseWhenSongEnd

  /** [minute] == -1 arms "pause after this song"; any other value starts a countdown. */
  fun start(minute: Int) {
    timerJob?.cancel()
    timerJob = null

    if (minute == -1) {
      pauseWhenSongEnd = true
      triggerTime = -1L
      return
    }

    pauseWhenSongEnd = false
    triggerTime = System.currentTimeMillis() + minute.minutes.inWholeMilliseconds
    timerJob =
      scope.launch {
        val remaining = triggerTime - System.currentTimeMillis()
        if (remaining > 0L) delay(remaining)
        fadeOutAndPause()
        triggerTime = -1L
      }
  }

  fun notifySongTransition() {
    if (!pauseWhenSongEnd) return
    pauseWhenSongEnd = false
    scope.launch { fadeOutAndPause() }
  }

  fun clear() {
    timerJob?.cancel()
    timerJob = null
    pauseWhenSongEnd = false
    triggerTime = -1L
  }

  override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
    notifySongTransition()
  }

  override fun onPlaybackStateChanged(playbackState: Int) {
    if (playbackState == Player.STATE_ENDED) notifySongTransition()
  }

  private suspend fun fadeOutAndPause() {
    val startVolume = player.volume
    repeat(FADE_STEPS) { step ->
      player.volume = startVolume * ((FADE_STEPS - step).toFloat() / FADE_STEPS)
      delay(FADE_STEP_MS)
    }
    player.pause()
    player.volume = startVolume
  }

  private companion object {
    const val FADE_STEPS = 30
    const val FADE_STEP_MS = 100L
  }
}
