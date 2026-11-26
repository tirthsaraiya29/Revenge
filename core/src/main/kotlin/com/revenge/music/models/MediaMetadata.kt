package com.revenge.music.models

import androidx.compose.runtime.Immutable
import java.io.Serializable
import java.time.LocalDateTime

@Immutable
data class MediaMetadata(
    val id: String,
    val title: String,
    val artists: List<Artist>,
    val duration: Int,
    val thumbnailUrl: String? = null,
    val album: Album? = null,
    val setVideoId: String? = null,
    val musicVideoType: String? = null,
    val explicit: Boolean = false,
    val liked: Boolean = false,
    val likedDate: LocalDateTime? = null,
    val inLibrary: LocalDateTime? = null,
    val libraryAddToken: String? = null,
    val libraryRemoveToken: String? = null,
    val suggestedBy: String? = null,
    val source: QueueItemSource = QueueItemSource.USER,
) : Serializable {

    val isVideoSong: Boolean
        get() = musicVideoType != null && musicVideoType != MUSIC_VIDEO_TYPE_ATV

    data class Artist(val id: String?, val name: String) : Serializable

    data class Album(val id: String, val title: String) : Serializable

    companion object {
        const val MUSIC_VIDEO_TYPE_ATV = "MUSIC_VIDEO_TYPE_ATV"
    }
}

enum class QueueItemSource { USER }
