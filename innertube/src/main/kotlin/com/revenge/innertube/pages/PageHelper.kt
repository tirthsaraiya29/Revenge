package com.revenge.innertube.pages

import com.revenge.innertube.models.Menu
import com.revenge.innertube.models.MusicResponsiveListItemRenderer.FlexColumn
import com.revenge.innertube.models.Run

/** Shared parsing helpers for song rows and their library-save menu entries. */
object PageHelper {
  // YouTube renamed the library icons in Feb 2026; KEEP/KEEP_OFF belong to "Pin to Listen Again"
  // and must not be mistaken for library state.
  private val ADD_ICONS = setOf("LIBRARY_ADD", "BOOKMARK_BORDER")
  private val SAVED_ICONS = setOf("LIBRARY_SAVED", "BOOKMARK", "LIBRARY_REMOVE")
  private val LIBRARY_ICONS = ADD_ICONS + SAVED_ICONS

  /** The two feedback tokens a song's menu carries, one for each library direction. */
  data class LibraryFeedbackTokens(val addToken: String?, val removeToken: String?)

  fun isLibraryIcon(iconType: String?): Boolean {
    if (iconType == null || iconType == "KEEP" || iconType == "KEEP_OFF") return false
    return iconType in LIBRARY_ICONS || iconType.startsWith("LIBRARY_")
  }

  fun isAddLibraryIcon(iconType: String?): Boolean = iconType in ADD_ICONS

  fun isSavedLibraryIcon(iconType: String?): Boolean = iconType in SAVED_ICONS

  /** Runs from [columns] whose endpoint music type contains [typeLike]. */
  fun extractRuns(columns: List<FlexColumn>, typeLike: String): List<Run> {
    val result = mutableListOf<Run>()
    for (column in columns) {
      val runs = column.musicResponsiveListItemFlexColumnRenderer.text?.runs ?: continue
      for (run in runs) {
        val type =
          run.navigationEndpoint?.watchEndpoint?.watchEndpointMusicSupportedConfigs
            ?.watchEndpointMusicConfig?.musicVideoType
            ?: run.navigationEndpoint?.browseEndpoint?.browseEndpointContextSupportedConfigs
              ?.browseEndpointContextMusicConfig?.pageType
            ?: continue
        if (typeLike in type) result.add(run)
      }
    }
    return result
  }

  /**
   * Reads both library tokens from a row's toggle menu entries. The default icon tells which
   * direction the default action takes, so the two tokens are assigned accordingly.
   */
  fun extractLibraryTokensFromMenuItems(items: List<Menu.MenuRenderer.Item>?): LibraryFeedbackTokens {
    if (items == null) return LibraryFeedbackTokens(null, null)

    var add: String? = null
    var remove: String? = null
    for (item in items) {
      val toggle = item.toggleMenuServiceItemRenderer ?: continue
      val icon = toggle.defaultIcon.iconType
      if (!isLibraryIcon(icon)) continue

      val defaultToken = toggle.defaultServiceEndpoint.feedbackEndpoint?.feedbackToken
      val toggledToken = toggle.toggledServiceEndpoint?.feedbackEndpoint?.feedbackToken
      if (isAddLibraryIcon(icon)) {
        if (add == null) add = defaultToken
        if (remove == null) remove = toggledToken
      } else {
        if (remove == null) remove = defaultToken
        if (add == null) add = toggledToken
      }
    }
    return LibraryFeedbackTokens(add, remove)
  }

  /** The token for [type] (`LIBRARY_ADD` or `LIBRARY_REMOVE`) from a single toggle entry. */
  fun extractFeedbackToken(
    menu: Menu.MenuRenderer.Item.ToggleMenuServiceRenderer?,
    type: String,
  ): String? {
    if (menu == null) return null
    val defaultToken = menu.defaultServiceEndpoint.feedbackEndpoint?.feedbackToken
    val toggledToken = menu.toggledServiceEndpoint?.feedbackEndpoint?.feedbackToken
    val notSaved = menu.defaultIcon.iconType in ADD_ICONS

    return when (type) {
      "LIBRARY_ADD" -> if (notSaved) defaultToken else toggledToken
      "LIBRARY_REMOVE", "LIBRARY_SAVED" -> if (notSaved) toggledToken else defaultToken
      else -> if (menu.defaultIcon.iconType == type) defaultToken else toggledToken
    }
  }
}
