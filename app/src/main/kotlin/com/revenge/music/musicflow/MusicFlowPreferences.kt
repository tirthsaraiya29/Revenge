package com.revenge.music.musicflow

import androidx.datastore.preferences.core.Preferences
import com.revenge.music.constants.CanvasThumbnailAnimationKey
import com.revenge.music.constants.DataSaverEnabledKey
import com.revenge.music.constants.MusicFlowAnimatedBackgroundKey
import com.revenge.music.constants.MusicFlowAutoNextKey
import com.revenge.music.constants.MusicFlowAutoplayKey
import com.revenge.music.constants.MusicFlowCellularPrefetchKey
import com.revenge.music.constants.MusicFlowEnabledKey
import com.revenge.music.constants.MusicFlowHideRecentKey
import com.revenge.music.constants.MusicFlowLyricsKey

data class MusicFlowPreferences(
    val enabled: Boolean,
    val autoplay: Boolean,
    val autoNext: Boolean,
    val lyrics: Boolean,
    val cellularPrefetch: Boolean,
    val hideRecent: Boolean,
    val animatedBackground: Boolean,
    val dataSaver: Boolean,
) {
    companion object {
        @JvmStatic
        fun from(preferences: Preferences): MusicFlowPreferences {
            val animated = preferences[MusicFlowAnimatedBackgroundKey]
                ?: preferences[CanvasThumbnailAnimationKey]
                ?: false
            return MusicFlowPreferences(
                enabled = preferences[MusicFlowEnabledKey] ?: true,
                autoplay = preferences[MusicFlowAutoplayKey] ?: true,
                autoNext = preferences[MusicFlowAutoNextKey] ?: true,
                lyrics = preferences[MusicFlowLyricsKey] ?: true,
                cellularPrefetch = preferences[MusicFlowCellularPrefetchKey] ?: true,
                hideRecent = preferences[MusicFlowHideRecentKey] ?: true,
                animatedBackground = animated,
                dataSaver = preferences[DataSaverEnabledKey] ?: true,
            )
        }

        fun defaults(): MusicFlowPreferences = MusicFlowPreferences(
            enabled = true,
            autoplay = true,
            autoNext = true,
            lyrics = true,
            cellularPrefetch = true,
            hideRecent = true,
            animatedBackground = false,
            dataSaver = true,
        )
    }
}
