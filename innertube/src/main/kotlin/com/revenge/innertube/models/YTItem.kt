package com.revenge.innertube.models

import com.revenge.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import com.revenge.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_PODCAST_EPISODE

/** Anything that can be rendered as a row or tile in the app. */
sealed class YTItem {
  abstract val id: String
  abstract val title: String
  abstract val thumbnail: String?
  abstract val explicit: Boolean
  abstract val shareLink: String
}

data class Artist(
  val name: String,
  val id: String?,
)

data class Album(
  val name: String,
  val id: String,
)

data class SongItem(
  override val id: String,
  override val title: String,
  val artists: List<Artist>,
  val album: Album? = null,
  val duration: Int? = null,
  val musicVideoType: String? = null,
  val chartPosition: Int? = null,
  val chartChange: String? = null,
  override val thumbnail: String,
  override val explicit: Boolean = false,
  val endpoint: WatchEndpoint? = null,
  val setVideoId: String? = null,
  val libraryAddToken: String? = null,
  val libraryRemoveToken: String? = null,
  val historyRemoveToken: String? = null,
  /** Podcast episode publication date as YouTube shows it, e.g. "19 dic 2025". */
  val publishedText: String? = null,
  /** Podcast episode progress tracked by YouTube, as a percentage. */
  val playedPercent: Int? = null,
  /** Podcast episode description, which often lists chapters. */
  val description: String? = null,
) : YTItem() {
  val isVideoSong: Boolean
    get() = musicVideoType != null && musicVideoType != MUSIC_VIDEO_TYPE_ATV

  val isPodcastEpisode: Boolean
    get() = musicVideoType == MUSIC_VIDEO_TYPE_PODCAST_EPISODE

  override val shareLink: String
    get() = "https://music.youtube.com/watch?v=$id"
}

data class AlbumItem(
  val browseId: String,
  val playlistId: String,
  override val id: String = browseId,
  override val title: String,
  val artists: List<Artist>?,
  val year: Int? = null,
  override val thumbnail: String,
  override val explicit: Boolean = false,
  val description: String? = null,
) : YTItem() {
  override val shareLink: String
    get() = "https://music.youtube.com/playlist?list=$playlistId"
}

data class PlaylistItem(
  override val id: String,
  override val title: String,
  val author: Artist?,
  val songCountText: String?,
  override val thumbnail: String?,
  val playEndpoint: WatchEndpoint?,
  val shuffleEndpoint: WatchEndpoint?,
  val radioEndpoint: WatchEndpoint?,
  val isEditable: Boolean = false,
  /** A podcast show: [id] is the show's playlist id and its entries are episodes. */
  val isPodcast: Boolean = false,
) : YTItem() {
  override val explicit: Boolean
    get() = false

  override val shareLink: String
    get() = "https://music.youtube.com/playlist?list=$id"
}

data class ArtistItem(
  override val id: String,
  override val title: String,
  override val thumbnail: String?,
  val channelId: String? = null,
  val playEndpoint: WatchEndpoint? = null,
  val shuffleEndpoint: WatchEndpoint?,
  val radioEndpoint: WatchEndpoint?,
) : YTItem() {
  override val explicit: Boolean
    get() = false

  override val shareLink: String
    get() = "https://music.youtube.com/channel/$id"
}

fun <T : YTItem> List<T>.filterExplicit(enabled: Boolean = true): List<T> =
  if (enabled) filterNot { it.explicit } else this

fun <T : YTItem> List<T>.filterVideoSongs(disableVideos: Boolean = false): List<T> =
  if (disableVideos) {
    // Podcast episodes are video-typed but audio content, so they stay.
    filterNot { it is SongItem && it.isVideoSong && !it.isPodcastEpisode }
  } else {
    this
  }

/** Removes podcast episodes and shows, which live in their own browse surface. */
fun <T : YTItem> List<T>.filterPodcasts(): List<T> =
  filterNot { (it is SongItem && it.isPodcastEpisode) || (it is PlaylistItem && it.isPodcast) }

fun <T : YTItem> List<T>.filterYoutubeShorts(enabled: Boolean = false): List<T> =
  if (enabled) filterNot { it is PlaylistItem && it.id.startsWith("SS") } else this

fun <T : YTItem> List<T>.filterBlockedArtists(
  blockedArtists: Set<String> = com.revenge.innertube.YouTube.blockedArtists,
): List<T> {
  if (blockedArtists.isEmpty()) return this

  // Entries are either a bare id or "id||name".
  val blockedIds = blockedArtists.map { it.substringBefore("||") }.toSet()
  val blockedNames =
    blockedArtists.mapNotNull { if (it.contains("||")) it.substringAfter("||") else null }.toSet()

  return filterNot { item ->
    when (item) {
      is ArtistItem ->
        item.id in blockedIds ||
          item.channelId in blockedIds ||
          item.title in blockedNames
      is SongItem -> item.artists.any { it.id in blockedIds || it.name in blockedNames }
      is AlbumItem -> item.artists?.any { it.id in blockedIds || it.name in blockedNames } ?: false
      is PlaylistItem ->
        item.author?.let { it.id in blockedIds || it.name in blockedNames } ?: false
      else -> false
    }
  }
}

fun <T : YTItem> List<T>.filterDislikedSongs(
  dislikedSongs: Set<String> = com.revenge.innertube.YouTube.dislikedSongs,
): List<T> {
  if (dislikedSongs.isEmpty()) return this
  val dislikedIds = dislikedSongs.map { it.substringBefore("||") }.toSet()
  return filterNot { it is SongItem && it.id in dislikedIds }
}
