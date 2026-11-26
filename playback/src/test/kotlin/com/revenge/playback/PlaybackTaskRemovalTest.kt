package com.revenge.playback

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTaskRemovalTest {

    @Test
    fun keepsPlayingWhileBufferingOrReady() {
        assertFalse(
            shouldStopPlaybackOnTaskRemoval(
                stopOnTaskClear = false,
                playWhenReady = true,
                playbackState = Player.STATE_BUFFERING,
            ),
        )
        assertFalse(
            shouldStopPlaybackOnTaskRemoval(
                stopOnTaskClear = false,
                playWhenReady = true,
                playbackState = Player.STATE_READY,
            ),
        )
    }

    @Test
    fun preferenceAlwaysWins() {
        assertTrue(shouldStopPlaybackOnTaskRemoval(true, true, Player.STATE_READY))
        assertTrue(shouldStopPlaybackOnTaskRemoval(true, true, Player.STATE_BUFFERING))
        assertTrue(shouldStopPlaybackOnTaskRemoval(true, false, Player.STATE_IDLE))
    }

    @Test
    fun pausedIdleAndEndedStatesStop() {
        assertTrue(shouldStopPlaybackOnTaskRemoval(false, false, Player.STATE_READY))
        assertTrue(shouldStopPlaybackOnTaskRemoval(false, true, Player.STATE_IDLE))
        assertTrue(shouldStopPlaybackOnTaskRemoval(false, true, Player.STATE_ENDED))
    }
}
