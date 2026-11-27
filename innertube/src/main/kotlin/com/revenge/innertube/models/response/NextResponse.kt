package com.revenge.innertube.models.response

import com.revenge.innertube.models.NavigationEndpoint
import com.revenge.innertube.models.PlaylistPanelRenderer
import com.revenge.innertube.models.Tabs
import com.revenge.innertube.models.YouTubeDataPage
import kotlinx.serialization.Serializable

@Serializable
data class NextResponse(
  val contents: Contents,
  val engagementPanels: List<EngagementPanel>? = null,
  val continuationContents: ContinuationContents?,
  val currentVideoEndpoint: NavigationEndpoint?,
) {
  @Serializable
  data class Contents(
    val singleColumnMusicWatchNextResultsRenderer: SingleColumnMusicWatchNextResultsRenderer?,
    val twoColumnWatchNextResults: YouTubeDataPage.Contents.TwoColumnWatchNextResults?,
  )

  @Serializable
  data class ContinuationContents(
    val playlistPanelContinuation: PlaylistPanelRenderer,
  )

  @Serializable
  data class EngagementPanel(
    val engagementPanelSectionListRenderer: EngagementPanelSectionListRenderer?,
  ) {
    @Serializable
    data class EngagementPanelSectionListRenderer(
      val panelIdentifier: String?,
      val content: Content?,
    ) {
      @Serializable
      data class Content(
        val sectionListRenderer: SectionListRenderer? = null,
      )

      @Serializable
      data class SectionListRenderer(
        val contents:
          List<YouTubeDataPage.Contents.TwoColumnWatchNextResults.Results.Results.Content>? =
          null,
      )
    }
  }
}

@Serializable
data class SingleColumnMusicWatchNextResultsRenderer(
  val tabbedRenderer: TabbedRenderer?,
)

@Serializable
data class TabbedRenderer(
  val watchNextTabbedResultsRenderer: WatchNextTabbedResultsRenderer?,
)

@Serializable
data class WatchNextTabbedResultsRenderer(
  val tabs: List<Tabs.Tab>,
)
