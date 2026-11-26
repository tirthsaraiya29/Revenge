package com.revenge.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class SearchSuggestionsSectionRenderer(
    val contents: List<Content>,
) {
    @Serializable
    data class Content(
        val searchSuggestionRenderer: SearchSuggestionRenderer?,
        val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer?,
    )

    @Serializable
    data class SearchSuggestionRenderer(
        val suggestion: Runs,
        val navigationEndpoint: NavigationEndpoint,
    )
}

data class SearchSuggestions(
    val queries: List<String>,
    val recommendedItems: List<YTItem>,
)
