package com.revenge.music.playback.crossfade

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Prepares the standby player once the remaining time drops under the adaptive lead, waits for it
 * to become READY, then blends the two players with equal-power gains.
 */
class StandbyCrossfadeSchedulerImpl(
  private val scope: CoroutineScope,
  override val preArmManager: AdaptivePreArmManager = AdaptivePreArmManager(),
) : StandbyCrossfadeScheduler {

  private val fading = MutableStateFlow(false)
  override val isCrossfading: StateFlow<Boolean> = fading.asStateFlow()

  private val armed = MutableStateFlow<ArmedStatus>(ArmedStatus.Idle)
  override val armedState: StateFlow<ArmedStatus> = armed.asStateFlow()

  override var onCrossfadeSwapped:
    ((outgoingPlayer: ExoPlayer, incomingPlayer: ExoPlayer) -> Unit)? =
    null

  private var nextItem: MediaItem? = null
  private var fadeDurationMs = 0L
  private var standby: ExoPlayer? = null
  private var standbyListener: Player.Listener? = null
  private var fadeJob: Job? = null
  private var prepStartedAt = 0L
  private var primaryDuringFade: ExoPlayer? = null

  override fun onQueueOrTrackChanged(
    primaryPlayer: ExoPlayer,
    nextItem: MediaItem?,
    crossfadeDurationMs: Long,
  ) {
    if (this.nextItem?.mediaId != nextItem?.mediaId) cancelCrossfade()
    this.nextItem = nextItem
    fadeDurationMs = crossfadeDurationMs
  }

  override fun onPlaybackPositionTick(
    primaryPlayer: ExoPlayer,
    currentPositionMs: Long,
    durationMs: Long,
    playerFactory: () -> ExoPlayer,
  ) {
    val target = nextItem ?: return
    if (fadeDurationMs <= 0L || durationMs <= fadeDurationMs) return

    val remainingMs = durationMs - currentPositionMs
    val leadMs = preArmManager.currentLeadTimeMs

    if (
      armed.value is ArmedStatus.Idle &&
        remainingMs > fadeDurationMs &&
        remainingMs <= leadMs
    ) {
      startPreArm(target, playerFactory)
    }

    if (armed.value is ArmedStatus.Armed && remainingMs <= fadeDurationMs && !fading.value) {
      beginFade(primaryPlayer)
    }
  }

  private fun startPreArm(target: MediaItem, playerFactory: () -> ExoPlayer) {
    prepStartedAt = System.currentTimeMillis()
    armed.value = ArmedStatus.Preparing(target.mediaId, prepStartedAt)

    val player = playerFactory()
    standby = player
    player.volume = 0f
    player.playWhenReady = false

    val listener =
      object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
          this@StandbyCrossfadeSchedulerImpl.onStandbyPlayerStateChanged(playbackState)
        }
      }
    standbyListener = listener
    player.addListener(listener)

    player.setMediaItem(target)
    player.prepare()
    // The player may already be READY synchronously (e.g. cached media).
    if (player.playbackState == Player.STATE_READY) {
      onStandbyPlayerStateChanged(Player.STATE_READY)
    }
  }

  fun onStandbyPlayerStateChanged(playbackState: Int) {
    val preparing = armed.value as? ArmedStatus.Preparing ?: return
    if (playbackState != Player.STATE_READY) return

    val prepareMs = System.currentTimeMillis() - prepStartedAt
    if (prepareMs > 0L) preArmManager.recordPreparationDuration(prepareMs)
    standby?.let { armed.value = ArmedStatus.Armed(it, preparing.targetMediaId) }
  }

  override fun triggerCrossfadeNow(primaryPlayer: ExoPlayer) {
    if (!fading.value && armed.value is ArmedStatus.Armed) beginFade(primaryPlayer)
  }

  private fun beginFade(primaryPlayer: ExoPlayer) {
    val incoming = standby ?: return
    if (fading.value) return

    primaryDuringFade = primaryPlayer
    fading.value = true
    incoming.playWhenReady = true

    fadeJob?.cancel()
    fadeJob =
      scope.launch {
        val steps = (fadeDurationMs / STEP_MS).toInt().coerceAtLeast(MIN_STEPS)
        for (step in 0..steps) {
          val progress = step.toFloat() / steps
          armed.value = ArmedStatus.Fading(progress)
          val gains = EqualPowerCurve.calculateGains(progress)
          safeSetVolume(primaryPlayer, gains.outgoingGain)
          safeSetVolume(incoming, gains.incomingGain)
          delay(STEP_MS)
        }

        // Restore unity before handing the incoming player over.
        safeSetVolume(primaryPlayer, 1f)
        safeSetVolume(incoming, 1f)

        fading.value = false
        armed.value = ArmedStatus.Idle
        standbyListener?.let(incoming::removeListener)
        standbyListener = null
        standby = null
        primaryDuringFade = null
        onCrossfadeSwapped?.invoke(primaryPlayer, incoming)
      }
  }

  override fun cancelCrossfade() {
    fadeJob?.cancel()
    fadeJob = null
    fading.value = false
    armed.value = ArmedStatus.Idle

    primaryDuringFade?.let { safeSetVolume(it, 1f) }
    primaryDuringFade = null

    standby?.let { player ->
      runCatching {
        standbyListener?.let(player::removeListener)
        player.stop()
        player.clearMediaItems()
        player.release()
      }
    }
    standbyListener = null
    standby = null
  }

  override fun release() {
    cancelCrossfade()
  }

  private fun safeSetVolume(player: ExoPlayer, volume: Float) {
    runCatching { player.volume = volume }
  }

  private companion object {
    const val STEP_MS = 20L
    const val MIN_STEPS = 10
  }
}
