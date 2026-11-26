package com.revenge.playback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SleepTimer(
    private val scope: CoroutineScope,
    var player: Player,
) : Player.Listener {

    var triggerTime: Long by mutableLongStateOf(-1L)
        private set

    var pauseWhenSongEnd: Boolean by mutableStateOf(false)
        private set

    val isActive: Boolean
        get() = triggerTime != -1L || pauseWhenSongEnd

    private var countdownJob: Job? = null

    fun start(minute: Int) {
        countdownJob?.cancel()
        countdownJob = null

        if (minute == -1) {
            pauseWhenSongEnd = true
            triggerTime = -1L
            return
        }

        pauseWhenSongEnd = false
        val now = System.currentTimeMillis()
        val delayMs = minute.toLong() * 60_000L
        triggerTime = now + delayMs
        countdownJob = scope.launch {
            if (delayMs > 0L) delay(delayMs)
            fadeDownAndPause()
            triggerTime = -1L
        }
    }

    fun notifySongTransition() {
        if (!pauseWhenSongEnd) return
        pauseWhenSongEnd = false
        scope.launch { fadeDownAndPause() }
    }

    fun clear() {
        countdownJob?.cancel()
        countdownJob = null
        pauseWhenSongEnd = false
        triggerTime = -1L
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        notifySongTransition()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) notifySongTransition()
    }

    private suspend fun fadeDownAndPause() {
        val startVolume = player.volume
        for (remaining in FADE_STEPS downTo 1) {
            player.volume = startVolume * (remaining.toFloat() / FADE_STEPS)
            delay(FADE_STEP_DELAY_MS)
        }
        player.pause()
        player.volume = startVolume
    }

    private companion object {
        const val FADE_STEPS = 30
        const val FADE_STEP_DELAY_MS = 100L
    }
}
