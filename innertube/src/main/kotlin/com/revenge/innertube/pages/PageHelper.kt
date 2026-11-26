package com.revenge.innertube.pages

import com.revenge.innertube.models.Badges
import com.revenge.innertube.models.Button
import com.revenge.innertube.models.Menu
import com.revenge.innertube.models.MusicResponsiveListItemRenderer
import com.revenge.innertube.models.NavigationEndpoint
import com.revenge.innertube.models.Run
import com.revenge.innertube.models.Runs
import com.revenge.innertube.models.ThumbnailRenderer
import com.revenge.innertube.models.WatchEndpoint
import com.revenge.innertube.utils.parseTime

object PageHelper {
    data class LibraryFeedbackTokens(
        val addToken: String?,
        val removeToken: String?,
    )

    private val ADD_ICONS = setOf("LIBRARY_ADD", "BOOKMARK_BORDER")
    private val SAVED_ICONS = setOf("LIBRARY_SAVED", "BOOKMARK", "LIBRARY_REMOVE")
    private val NON_LIBRARY_ICONS = setOf("KEEP", "KEEP_OFF")

    fun isLibraryIcon(iconType: String?): Boolean {
        if (iconType == null || iconType in NON_LIBRARY_ICONS) return false
        return iconType in ADD_ICONS || iconType in SAVED_ICONS || iconType.startsWith("LIBRARY_")
    }

    fun isAddLibraryIcon(iconType: String?): Boolean = iconType != null && iconType in ADD_ICONS

    fun isSavedLibraryIcon(iconType: String?): Boolean = iconType != null && iconType in SAVED_ICONS

    fun extractRuns(
        columns: List<MusicResponsiveListItemRenderer.FlexColumn>,
        typeLike: String,
    ): List<Run> {
        val result = mutableListOf<Run>()
        for (column in columns) {
            val runs = column.musicResponsiveListItemFlexColumnRenderer.text?.runs ?: continue
            for (run in runs) {
                val endpoint = run.navigationEndpoint
                val type = endpoint?.watchEndpoint
                    ?.watchEndpointMusicSupportedConfigs
                    ?.watchEndpointMusicConfig
                    ?.musicVideoType
                    ?: endpoint?.browseEndpoint?.pageType
                    ?: continue
                if (type.contains(typeLike)) result.add(run)
            }
        }
        return result
    }

    fun extractLibraryTokensFromMenuItems(
        items: List<Menu.MenuRenderer.Item>?,
    ): LibraryFeedbackTokens {
        if (items == null) return LibraryFeedbackTokens(null, null)
        var addToken: String? = null
        var removeToken: String? = null
        for (item in items) {
            val toggle = item.toggleMenuServiceItemRenderer ?: continue
            val iconType = toggle.defaultIcon.iconType
            if (!isLibraryIcon(iconType)) continue
            when {
                isAddLibraryIcon(iconType) -> {
                    if (addToken == null) addToken = toggle.defaultServiceEndpoint.feedbackEndpoint?.feedbackToken
                    if (removeToken == null) removeToken = toggle.toggledServiceEndpoint?.feedbackEndpoint?.feedbackToken
                }
                isSavedLibraryIcon(iconType) -> {
                    if (removeToken == null) removeToken = toggle.defaultServiceEndpoint.feedbackEndpoint?.feedbackToken
                    if (addToken == null) addToken = toggle.toggledServiceEndpoint?.feedbackEndpoint?.feedbackToken
                }
            }
        }
        return LibraryFeedbackTokens(addToken, removeToken)
    }

    fun extractFeedbackToken(
        menu: Menu.MenuRenderer.Item.ToggleMenuServiceRenderer?,
        type: String,
    ): String? {
        if (menu == null) return null
        val iconType = menu.defaultIcon.iconType
        val notSaved = isAddLibraryIcon(iconType)
        return when (type) {
            "LIBRARY_ADD" -> if (notSaved) {
                menu.defaultServiceEndpoint.feedbackEndpoint?.feedbackToken
            } else {
                menu.toggledServiceEndpoint?.feedbackEndpoint?.feedbackToken
            }
            "LIBRARY_REMOVE", "LIBRARY_SAVED" -> if (notSaved) {
                menu.toggledServiceEndpoint?.feedbackEndpoint?.feedbackToken
            } else {
                menu.defaultServiceEndpoint.feedbackEndpoint?.feedbackToken
            }
            else -> if (iconType == type) {
                menu.defaultServiceEndpoint.feedbackEndpoint?.feedbackToken
            } else {
                menu.toggledServiceEndpoint?.feedbackEndpoint?.feedbackToken
            }
        }
    }
}

internal fun ThumbnailRenderer?.thumbnailUrl(): String? =
    this?.musicThumbnailRenderer?.getThumbnailUrl()
        ?: this?.croppedSquareThumbnailRenderer?.getThumbnailUrl()

internal fun MusicResponsiveListItemRenderer.runsAt(index: Int): List<Run> =
    flexColumns.getOrNull(index)
        ?.musicResponsiveListItemFlexColumnRenderer
        ?.text
        ?.runs
        .orEmpty()

internal fun MusicResponsiveListItemRenderer.textAt(index: Int): String? =
    runsAt(index).firstOrNull()?.text

internal fun MusicResponsiveListItemRenderer.Overlay.playEndpoint(): NavigationEndpoint? =
    musicItemThumbnailOverlayRenderer.content.musicPlayButtonRenderer.playNavigationEndpoint

internal fun MusicResponsiveListItemRenderer.overlayPlayEndpoint(): NavigationEndpoint? =
    overlay?.playEndpoint()

internal fun MusicResponsiveListItemRenderer.findVideoId(): String? =
    playlistItemData?.videoId
        ?: navigationEndpoint?.watchEndpoint?.videoId
        ?: overlayPlayEndpoint()?.watchEndpoint?.videoId
        ?: runsAt(0).firstOrNull()?.navigationEndpoint?.watchEndpoint?.videoId

internal fun MusicResponsiveListItemRenderer.findSetVideoId(): String? =
    playlistItemData?.playlistSetVideoId
        ?: navigationEndpoint?.watchEndpoint?.playlistSetVideoId
        ?: overlayPlayEndpoint()?.watchEndpoint?.playlistSetVideoId
        ?: runsAt(0).firstOrNull()?.navigationEndpoint?.watchEndpoint?.playlistSetVideoId

internal fun MusicResponsiveListItemRenderer.durationSeconds(): Int? =
    fixedColumns
        ?.firstOrNull()
        ?.musicResponsiveListItemFlexColumnRenderer
        ?.text
        ?.runs
        ?.firstOrNull()
        ?.text
        ?.parseTime()

internal fun List<Badges>?.hasExplicitBadge(): Boolean =
    this?.any { it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE" } == true

internal fun MusicResponsiveListItemRenderer.libraryTokens(): PageHelper.LibraryFeedbackTokens =
    PageHelper.extractLibraryTokensFromMenuItems(menu?.menuRenderer?.items)

internal fun Menu?.findMenuWatchEndpoint(iconType: String): WatchEndpoint? {
    val items = this?.menuRenderer?.items.orEmpty()
    for (item in items) {
        val navigation = item.menuNavigationItemRenderer
            ?.takeIf { it.icon.iconType == iconType }
            ?.navigationEndpoint
            ?: item.menuServiceItemRenderer
                ?.takeIf { it.icon.iconType == iconType }
                ?.serviceEndpoint
        if (navigation != null) return navigation.anyWatchEndpoint
    }
    return null
}

internal fun Menu?.hasMenuIcon(iconType: String): Boolean {
    val items = this?.menuRenderer?.items.orEmpty()
    return items.any {
        it.menuNavigationItemRenderer?.icon?.iconType == iconType ||
            it.menuServiceItemRenderer?.icon?.iconType == iconType
    }
}

internal fun Menu?.historyRemoveToken(): String? =
    this?.menuRenderer?.items.orEmpty()
        .firstOrNull { it.menuServiceItemRenderer?.icon?.iconType == "REMOVE_FROM_HISTORY" }
        ?.menuServiceItemRenderer
        ?.serviceEndpoint
        ?.feedbackEndpoint
        ?.feedbackToken

internal fun List<Button>.findButtonWatchEndpoint(iconType: String): WatchEndpoint? =
    firstOrNull { it.buttonRenderer.icon?.iconType == iconType }
        ?.buttonRenderer
        ?.navigationEndpoint
        ?.anyWatchEndpoint
