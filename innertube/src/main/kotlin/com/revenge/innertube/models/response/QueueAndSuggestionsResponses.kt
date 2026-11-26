package com.revenge.innertube.models.response

import com.revenge.innertube.models.MusicShelfRenderer
import com.revenge.innertube.models.PlaylistPanelRenderer
import com.revenge.innertube.models.SearchSuggestionsSectionRenderer
import kotlinx.serialization.Serializable

@Serializable
data class GetQueueResponse(
  val queueDatas: List<QueueData>,
) {
  @Serializable
  data class QueueData(
    val content: PlaylistPanelRenderer.Content,
  )
}

@Serializable
data class GetSearchSuggestionsResponse(
  val contents: List<Content>?,
) {
  @Serializable
  data class Content(
    val searchSuggestionsSectionRenderer: SearchSuggestionsSectionRenderer,
  )
}

@Serializable
data class ContinuationResponse(
  val onResponseReceivedActions: List<ResponseAction>?,
) {
  @Serializable
  data class ResponseAction(
    val appendContinuationItemsAction: ContinuationItems?,
  )

  @Serializable
  data class ContinuationItems(
    val continuationItems: List<MusicShelfRenderer.Content>?,
  )
}
