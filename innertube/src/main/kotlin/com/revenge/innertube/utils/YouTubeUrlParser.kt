package com.revenge.innertube.utils

import com.revenge.innertube.models.WatchEndpoint

/** Recognises the YouTube video and YouTube Music artist URLs the app can open. */
object YouTubeUrlParser {

  sealed class ParsedUrl {
    abstract val id: String
  }

  data class Video(override val id: String) : ParsedUrl()

  data class Artist(override val id: String) : ParsedUrl()

  private const val VIDEO_ID = "[A-Za-z0-9_-]{11}"

  private val watchHost =
    Regex(
      "^(?:https?://)?(?:www\\.)?(?:music\\.)?youtube\\.com/watch\\?.*$",
      RegexOption.IGNORE_CASE,
    )
  private val watchParam = Regex("[?&]v=($VIDEO_ID)(?:[&#]|\\z)")
  private val shortLink =
    Regex(
      "^(?:https?://)?(?:www\\.)?youtu\\.be/($VIDEO_ID)(?:[?#/].*)?$",
      RegexOption.IGNORE_CASE,
    )
  private val shorts =
    Regex(
      "^(?:https?://)?(?:www\\.)?(?:music\\.)?youtube\\.com/shorts/($VIDEO_ID)(?:[?#/].*)?$",
      RegexOption.IGNORE_CASE,
    )
  private val artistChannel =
    Regex(
      "^(?:https?://)?music\\.youtube\\.com/channel/([A-Za-z0-9_-]+)(?:[?#/].*)?$",
      RegexOption.IGNORE_CASE,
    )
  private val artistBrowse =
    Regex(
      "^(?:https?://)?music\\.youtube\\.com/browse/(MPRE[A-Za-z0-9_-]+)(?:[?#/].*)?$",
      RegexOption.IGNORE_CASE,
    )

  fun isYouTubeUrl(text: String): Boolean = parse(text) != null

  fun parse(url: String): ParsedUrl? {
    val text = url.trim()
    extractVideoId(text)?.let {
      return Video(it)
    }
    if (!text.contains("music.youtube.com", ignoreCase = true)) return null
    artistChannel.find(text)?.let {
      return Artist(it.groupValues[1])
    }
    artistBrowse.find(text)?.let {
      return Artist(it.groupValues[1])
    }
    return null
  }

  fun extractVideoId(url: String): String? {
    val text = url.trim()
    shorts.find(text)?.let {
      return it.groupValues[1]
    }
    shortLink.find(text)?.let {
      return it.groupValues[1]
    }
    if (watchHost.matches(text)) return watchParam.find(text)?.groupValues?.get(1)
    return null
  }

  fun createWatchEndpoint(url: String): WatchEndpoint? {
    val id = extractVideoId(url) ?: return null
    return WatchEndpoint(videoId = id)
  }
}
