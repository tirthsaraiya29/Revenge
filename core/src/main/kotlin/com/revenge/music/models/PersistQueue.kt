package com.revenge.music.models

import java.io.Serializable

data class PersistQueue(
    val title: String?,
    val items: List<MediaMetadata>,
    val mediaItemIndex: Int,
    val position: Long,
    val queueType: QueueType = QueueType.LIST,
    val queueData: QueueData? = null,
    val shuffledIndices: List<Int>? = null,
) : Serializable

sealed class QueueType : Serializable {
    // Kotlin objects do not get a readResolve automatically, so deserialisation would otherwise
    // yield a distinct instance and break the documented singleton identity.
    object LIST : QueueType() {
        private fun readResolve(): Any = LIST
    }

    object YOUTUBE : QueueType() {
        private fun readResolve(): Any = YOUTUBE
    }

    object YOUTUBE_ALBUM_RADIO : QueueType() {
        private fun readResolve(): Any = YOUTUBE_ALBUM_RADIO
    }

    object LOCAL_ALBUM_RADIO : QueueType() {
        private fun readResolve(): Any = LOCAL_ALBUM_RADIO
    }
}

sealed class QueueData : Serializable {
    data class YouTubeData(
        val endpoint: String,
        val continuation: String? = null,
    ) : QueueData()

    data class YouTubeAlbumRadioData(
        val playlistId: String,
        val albumSongCount: Int = 0,
        val continuation: String? = null,
        val firstTimeLoaded: Boolean = false,
    ) : QueueData()

    data class LocalAlbumRadioData(
        val albumId: String,
        val startIndex: Int = 0,
        val playlistId: String? = null,
        val continuation: String? = null,
        val firstTimeLoaded: Boolean = false,
    ) : QueueData()
}
