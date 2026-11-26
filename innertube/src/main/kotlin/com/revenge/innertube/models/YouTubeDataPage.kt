package com.revenge.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class YouTubeDataPage(
    val contents: Contents? = null,
) {
    @Serializable
    data class Contents(
        val twoColumnWatchNextResults: TwoColumnWatchNextResults? = null,
    ) {
        @Serializable
        data class TwoColumnWatchNextResults(
            val results: Results? = null,
        ) {
            @Serializable
            data class Results(
                val results: Results? = null,
            ) {
                @Serializable
                data class Results(
                    val contents: List<Content>? = null,
                ) {
                    @Serializable
                    data class Content(
                        val videoPrimaryInfoRenderer: VideoPrimaryInfoRenderer? = null,
                        val videoSecondaryInfoRenderer: VideoSecondaryInfoRenderer? = null,
                        val itemSectionRenderer: ItemSectionRenderer? = null,
                        val continuationItemRenderer: ContinuationItemRenderer? = null,
                    ) {
                        @Serializable
                        data class VideoPrimaryInfoRenderer(
                            val title: Runs? = null,
                            val viewCount: ViewCount? = null,
                            val dateText: SimpleText? = null,
                        ) {
                            @Serializable
                            data class ViewCount(
                                val videoViewCountRenderer: VideoViewCountRenderer? = null,
                            )

                            @Serializable
                            data class VideoViewCountRenderer(
                                val viewCount: SimpleText? = null,
                            )
                        }

                        @Serializable
                        data class VideoSecondaryInfoRenderer(
                            val owner: Owner? = null,
                            val attributedDescription: AttributedDescription? = null,
                        ) {
                            @Serializable
                            data class Owner(
                                val videoOwnerRenderer: VideoOwnerRenderer? = null,
                            )

                            @Serializable
                            data class VideoOwnerRenderer(
                                val thumbnail: Thumbnails? = null,
                                val subscriberCountText: SimpleText? = null,
                                val title: Runs? = null,
                                val navigationEndpoint: NavigationEndpoint? = null,
                            )

                            @Serializable
                            data class AttributedDescription(
                                val content: String? = null,
                            )
                        }

                        @Serializable
                        data class ItemSectionRenderer(
                            val contents: List<ItemSectionContent>? = null,
                            val sectionIdentifier: String? = null,
                        ) {
                            @Serializable
                            data class ItemSectionContent(
                                val continuationItemRenderer: ContinuationItemRenderer? = null,
                            )
                        }

                        @Serializable
                        data class ContinuationItemRenderer(
                            val trigger: String? = null,
                            val continuationEndpoint: ContinuationEndpoint? = null,
                        ) {
                            @Serializable
                            data class ContinuationEndpoint(
                                val continuationCommand: ContinuationCommand? = null,
                            )

                            @Serializable
                            data class ContinuationCommand(
                                val token: String? = null,
                                val request: String? = null,
                            )
                        }

                        @Serializable
                        data class SimpleText(
                            val simpleText: String? = null,
                        )
                    }
                }
            }
        }
    }
}
