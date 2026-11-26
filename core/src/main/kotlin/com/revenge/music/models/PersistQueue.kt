package com.revenge.music.models

import java.io.Serializable

/** Identifies which queue flavour produced a persisted snapshot. */
sealed class QueueType : Serializable {
  data object LIST : QueueType()

  data object YOUTUBE : QueueType()

  data object YOUTUBE_ALBUM_RADIO : QueueType()

  data object LOCAL_ALBUM_RADIO : QueueType()
}

/** Extra state needed to rebuild non-list queues after process death. */
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

/** Serializable snapshot of the live player queue. */
data class PersistQueue(
  val title: String?,
  val items: List<MediaMetadata>,
  val mediaItemIndex: Int,
  val position: Long,
  val queueType: QueueType = QueueType.LIST,
  val queueData: QueueData? = null,
  val shuffledIndices: List<Int>? = null,
) : Serializable
