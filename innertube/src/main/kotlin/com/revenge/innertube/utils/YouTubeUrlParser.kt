package com.revenge.innertube.utils

import com.revenge.innertube.models.WatchEndpoint

/** Recognises the YouTube and YouTube Music links the search box accepts. */
object YouTubeUrlParser {
  sealed class ParsedUrl {
    abstract val id: String

    data class Video(override val id: String) : ParsedUrl()

    data class Artist(override val id: String) : ParsedUrl()
  }

  private val VIDEO_PATTERNS =
    listOf(
      Regex("""(?:https?://)?(?:www\.)?(?:music\.)?youtube\.com/watch\?[^ ]*v=([a-zA-Z0-9_-]{11})"""),
      Regex("""(?:https?://)?youtu\.be/([a-zA-Z0-9_-]{11})"""),
      Regex("""(?:https?://)?(?:www\.)?youtube\.com/shorts/([a-zA-Z0-9_-]{11})"""),
    )

  private val ARTIST_PATTERNS =
    listOf(
      Regex("""(?:https?://)?(?:www\.)?music\.youtube\.com/channel/([a-zA-Z0-9_-]+)"""),
      Regex("""(?:https?://)?(?:www\.)?music\.youtube\.com/browse/(MPRE[a-zA-Z0-9_-]+)"""),
    )

  fun isYouTubeUrl(text: String): Boolean = parse(text) != null

  fun parse(url: String): ParsedUrl? {
    val trimmed = url.trim()
    for (pattern in VIDEO_PATTERNS) {
      pattern.find(trimmed)?.groupValues?.getOrNull(1)?.let { return ParsedUrl.Video(it) }
    }
    if (trimmed.contains("music.youtube.com")) {
      for (pattern in ARTIST_PATTERNS) {
        pattern.find(trimmed)?.groupValues?.getOrNull(1)?.let { return ParsedUrl.Artist(it) }
      }
    }
    return null
  }

  fun extractVideoId(url: String): String? = (parse(url) as? ParsedUrl.Video)?.id

  fun createWatchEndpoint(url: String): WatchEndpoint? =
    extractVideoId(url)?.let { WatchEndpoint(videoId = it) }
}
