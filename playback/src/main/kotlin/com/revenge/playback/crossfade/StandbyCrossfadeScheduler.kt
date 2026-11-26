package com.revenge.playback.crossfade

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.StateFlow

sealed interface ArmedStatus {
    data object Idle : ArmedStatus

    data class Preparing(
        val targetMediaId: String,
        val startedAt: Long,
    ) : ArmedStatus

    data class Armed(
        val standbyPlayer: ExoPlayer,
        val targetMediaId: String,
    ) : ArmedStatus

    data class Fading(val progress: Float) : ArmedStatus
}

interface StandbyCrossfadeScheduler {

    val isCrossfading: StateFlow<Boolean>

    val armedState: StateFlow<ArmedStatus>

    val preArmManager: AdaptivePreArmManager

    var onCrossfadeSwapped: ((outgoingPlayer: ExoPlayer, incomingPlayer: ExoPlayer) -> Unit)?

    fun onQueueOrTrackChanged(
        primaryPlayer: ExoPlayer,
        nextItem: MediaItem?,
        crossfadeDurationMs: Long,
    )

    fun onPlaybackPositionTick(
        primaryPlayer: ExoPlayer,
        currentPositionMs: Long,
        durationMs: Long,
        playerFactory: () -> ExoPlayer,
    )

    fun triggerCrossfadeNow(primaryPlayer: ExoPlayer)

    fun cancelCrossfade()

    fun release()
}
