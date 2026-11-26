package com.revenge.playback.crossfade

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class StandbyCrossfadeSchedulerImpl(
    private val scope: CoroutineScope,
    override val preArmManager: AdaptivePreArmManager = AdaptivePreArmManager(),
) : StandbyCrossfadeScheduler {

    private val crossfadingState = MutableStateFlow(false)
    override val isCrossfading: StateFlow<Boolean> = crossfadingState

    private val armedStateFlow = MutableStateFlow<ArmedStatus>(ArmedStatus.Idle)
    override val armedState: StateFlow<ArmedStatus> = armedStateFlow

    override var onCrossfadeSwapped: ((outgoingPlayer: ExoPlayer, incomingPlayer: ExoPlayer) -> Unit)? = null

    private var targetItem: MediaItem? = null
    private var targetMediaId: String? = null
    private var crossfadeDurationMs: Long = 0L

    private var standbyPlayer: ExoPlayer? = null
    private var activePrimary: ExoPlayer? = null
    private var preparationStartedAt: Long = 0L
    private var fadeJob: Job? = null

    private val standbyListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            this@StandbyCrossfadeSchedulerImpl.onStandbyPlayerStateChanged(playbackState)
        }
    }

    override fun onQueueOrTrackChanged(
        primaryPlayer: ExoPlayer,
        nextItem: MediaItem?,
        crossfadeDurationMs: Long,
    ) {
        val nextId = nextItem?.mediaId
        if (nextId != targetMediaId) cancelCrossfade()
        targetItem = nextItem
        targetMediaId = nextId
        this.crossfadeDurationMs = crossfadeDurationMs
    }

    override fun onPlaybackPositionTick(
        primaryPlayer: ExoPlayer,
        currentPositionMs: Long,
        durationMs: Long,
        playerFactory: () -> ExoPlayer,
    ) {
        val target = targetItem ?: return
        val fadeDuration = crossfadeDurationMs
        if (fadeDuration <= 0L || durationMs <= fadeDuration) return

        val remaining = durationMs - currentPositionMs
        val lead = preArmManager.currentLeadTimeMs
        when (val state = armedStateFlow.value) {
            is ArmedStatus.Idle -> {
                if (remaining > fadeDuration && remaining <= lead) {
                    prepareStandby(target, playerFactory)
                }
            }

            is ArmedStatus.Armed -> {
                if (remaining <= fadeDuration && fadeJob == null) {
                    startFade(primaryPlayer, state)
                }
            }

            else -> Unit
        }
    }

    override fun triggerCrossfadeNow(primaryPlayer: ExoPlayer) {
        if (fadeJob != null) return
        val state = armedStateFlow.value
        if (state is ArmedStatus.Armed) startFade(primaryPlayer, state)
    }

    override fun cancelCrossfade() {
        fadeJob?.cancel()
        fadeJob = null

        val wasFading = crossfadingState.value
        crossfadingState.value = false
        armedStateFlow.value = ArmedStatus.Idle
        if (wasFading) setVolumeSafely(activePrimary, 1f)

        val standby = standbyPlayer
        standbyPlayer = null
        if (standby != null) {
            runCatching { standby.removeListener(standbyListener) }
            runCatching { standby.stop() }
            runCatching { standby.clearMediaItems() }
            runCatching { standby.release() }
        }

        activePrimary = null
        targetItem = null
        targetMediaId = null
    }

    override fun release() = cancelCrossfade()

    fun onStandbyPlayerStateChanged(playbackState: Int) {
        if (playbackState != Player.STATE_READY) return
        val state = armedStateFlow.value
        if (state !is ArmedStatus.Preparing) return
        val standby = standbyPlayer ?: return

        val preparationMs = System.currentTimeMillis() - preparationStartedAt
        if (preparationMs > 0L) preArmManager.recordPreparationDuration(preparationMs)
        armedStateFlow.value = ArmedStatus.Armed(standby, state.targetMediaId)
    }

    private fun prepareStandby(target: MediaItem, playerFactory: () -> ExoPlayer) {
        val startedAt = System.currentTimeMillis()
        preparationStartedAt = startedAt
        armedStateFlow.value = ArmedStatus.Preparing(target.mediaId, startedAt)

        val standby = playerFactory()
        standbyPlayer = standby
        standby.volume = 0f
        standby.playWhenReady = false
        standby.addListener(standbyListener)
        standby.setMediaItem(target)
        standby.prepare()

        if (standby.playbackState == Player.STATE_READY) {
            onStandbyPlayerStateChanged(Player.STATE_READY)
        }
    }

    private fun startFade(primary: ExoPlayer, armed: ArmedStatus.Armed) {
        if (fadeJob != null) return
        val duration = crossfadeDurationMs
        if (duration <= 0L) return

        val incoming = armed.standbyPlayer
        activePrimary = primary
        crossfadingState.value = true
        armedStateFlow.value = ArmedStatus.Fading(0f)

        val steps = maxOf(1, (duration / FADE_INTERVAL_MS).toInt())
        fadeJob = scope.launch {
            runCatching { incoming.play() }
            for (step in 1..steps) {
                delay(FADE_INTERVAL_MS)
                val progress = step.toFloat() / steps
                val gains = EqualPowerCurve.calculateGains(progress)
                setVolumeSafely(primary, gains.outgoingGain)
                setVolumeSafely(incoming, gains.incomingGain)
                armedStateFlow.value = ArmedStatus.Fading(progress)
            }
            finishFade(primary, incoming)
        }
    }

    private fun finishFade(primary: ExoPlayer, incoming: ExoPlayer) {
        setVolumeSafely(primary, 1f)
        setVolumeSafely(incoming, 1f)
        crossfadingState.value = false
        armedStateFlow.value = ArmedStatus.Idle
        runCatching { incoming.removeListener(standbyListener) }
        standbyPlayer = null
        activePrimary = null
        fadeJob = null
        onCrossfadeSwapped?.invoke(primary, incoming)
    }

    private fun setVolumeSafely(player: ExoPlayer?, volume: Float) {
        if (player == null) return
        try {
            player.volume = volume
        } catch (_: Throwable) {
            // A player released mid-fade must not abort the remaining gain updates.
        }
    }

    private companion object {
        const val FADE_INTERVAL_MS = 20L
    }
}
