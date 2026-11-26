package com.revenge.innertube.models.response

import kotlinx.serialization.Serializable

@Serializable
data class AddItemYouTubePlaylistResponse(
  val status: String,
  val playlistEditResults: List<PlaylistEditResult>,
) {
  @Serializable
  data class PlaylistEditResult(
    val playlistEditVideoAddedResultData: PlaylistEditVideoAddedResultData,
  ) {
    @Serializable
    data class PlaylistEditVideoAddedResultData(val setVideoId: String, val videoId: String)
  }
}

@Serializable data class CreatePlaylistResponse(val playlistId: String)

@Serializable
data class EditPlaylistResponse(
  val newHeader: BrowseResponse.Header?,
)

@Serializable
data class FeedbackResponse(
  val feedbackResponses: List<Status>,
) {
  @Serializable
  data class Status(
    val isProcessed: Boolean,
  )
}

@Serializable data class ImageUploadResponse(val encryptedBlobId: String)
