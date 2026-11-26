package com.revenge.music.playback.crossfade

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.StateFlow

/** Observable phase of the standby player / crossfade lifecycle. */
sealed interface ArmedStatus {
  data object Idle : ArmedStatus

  data class Preparing(val targetMediaId: String, val startedAt: Long) : ArmedStatus

  data class Armed(val standbyPlayer: ExoPlayer, val targetMediaId: String) : ArmedStatus

  data class Fading(val progress: Float) : ArmedStatus
}

/**
 * Owns a standby [ExoPlayer] that is prepared ahead of a track transition and then faded in while
 * the primary player fades out.
 */
interface StandbyCrossfadeScheduler {
  val isCrossfading: StateFlow<Boolean>
  val armedState: StateFlow<ArmedStatus>
  val preArmManager: AdaptivePreArmManager

  /**
   * Invoked after the fade completes. [outgoingPlayer] has just faded out and [incomingPlayer] has
   * just faded in; the caller owns their lifecycle from here on.
   */
  var onCrossfadeSwapped: ((outgoingPlayer: ExoPlayer, incomingPlayer: ExoPlayer) -> Unit)?

  /** Updates the next item and fade length; a different target aborts any pending work. */
  fun onQueueOrTrackChanged(
    primaryPlayer: ExoPlayer,
    nextItem: MediaItem?,
    crossfadeDurationMs: Long,
  )

  /** Called often enough by the playback clock to arm and then start the crossfade. */
  fun onPlaybackPositionTick(
    primaryPlayer: ExoPlayer,
    currentPositionMs: Long,
    durationMs: Long,
    playerFactory: () -> ExoPlayer,
  )

  /** Starts the fade immediately when the standby player is armed. */
  fun triggerCrossfadeNow(primaryPlayer: ExoPlayer)

  /** Drops any prepared standby player and restores primary volume (pause, seek, queue clear). */
  fun cancelCrossfade()

  /** Frees the standby player. */
  fun release()
}
