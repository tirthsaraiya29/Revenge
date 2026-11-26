package com.revenge.music.ui.screens.ambient

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.revenge.applecanvas.AppleMusicCanvasProvider
import com.revenge.canvas.CanvasArtwork
import com.revenge.canvas.TidalCanvasProvider
import com.revenge.innertube.models.PODCAST_BROWSE_PREFIX
import com.revenge.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_PODCAST_EPISODE
import com.revenge.music.LocalPlayerConnection
import com.revenge.core.constants.CanvasThumbnailAnimationKey
import com.revenge.core.constants.DataSaverEnabledKey
import com.revenge.music.spotify.Spotify
import com.revenge.music.ui.player.CanvasArtworkPlaybackCache
import com.revenge.music.ui.player.CanvasArtworkPlayer
import com.revenge.music.ui.player.normalizeCanvasArtistName
import com.revenge.music.ui.player.normalizeCanvasSongTitle
import com.revenge.music.utils.PodcastProgress
import com.revenge.music.utils.rememberPreference
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AmbientCanvasBackground(modifier: Modifier = Modifier) {
  val playerConnection = LocalPlayerConnection.current ?: return
  val context = LocalContext.current
  val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
  val isPlaying by playerConnection.isPlaying.collectAsState()
  val metadata = mediaMetadata ?: return

  val dataSaverEnabled by rememberPreference(key = DataSaverEnabledKey, defaultValue = false)
  val enableCanvasPref by rememberPreference(CanvasThumbnailAnimationKey, defaultValue = false)
  val enableCanvas = if (dataSaverEnabled) false else enableCanvasPref

  var canvasArtwork by remember(metadata.id) { mutableStateOf<CanvasArtwork?>(null) }

  val storefront = remember {
    val country = Locale.getDefault().country
    if (country.length == 2) country.lowercase(Locale.ROOT) else "us"
  }

  val isEpisode = metadata.let { meta ->
    val durationMs = meta.duration * 1000L
    val podcastProgress = PodcastProgress.getInstance(context)
    podcastProgress.get(meta.id) != null ||
      podcastProgress.isEpisode(meta, durationMs) ||
      meta.musicVideoType == MUSIC_VIDEO_TYPE_PODCAST_EPISODE ||
      meta.artists.any { it.id?.startsWith(PODCAST_BROWSE_PREFIX) == true }
  }

  LaunchedEffect(metadata.id, enableCanvas, isEpisode) {
    if (isEpisode || !enableCanvas) {
      canvasArtwork = null
      return@LaunchedEffect
    }

    canvasArtwork = CanvasArtworkPlaybackCache.get(metadata.id)
    if (canvasArtwork != null) return@LaunchedEffect

    val itemMetadata = playerConnection.player.currentMediaItem?.mediaMetadata
    val albumName = itemMetadata?.albumTitle?.toString().orEmpty()
    val songTitleRaw = itemMetadata?.title?.toString().orEmpty()
    val artistNameRaw = itemMetadata?.artist?.toString().orEmpty()

    val fetched =
      withContext(Dispatchers.IO) {
        val songTitle = normalizeCanvasSongTitle(songTitleRaw)
        val artistName = normalizeCanvasArtistName(artistNameRaw)

        val genuineCanvas =
          linkedSetOf(
              songTitle to artistName,
              songTitleRaw to artistName,
              songTitle to artistNameRaw,
              songTitleRaw to artistNameRaw,
            )
            .filter { (song, artist) -> song.isNotBlank() && artist.isNotBlank() }
            .firstNotNullOfOrNull { (song, artist) ->
              if (albumName.isNotBlank()) {
                AppleMusicCanvasProvider.getByAlbumArtist(
                    album = albumName,
                    artist = artist,
                    storefront = storefront,
                  )
                  ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                  ?.let {
                    return@firstNotNullOfOrNull it
                  }
              }

              Spotify.getCanvasForSong(title = song, artist = artist)?.let { spotifyUrl ->
                return@firstNotNullOfOrNull CanvasArtwork(
                  name = song,
                  artist = artist,
                  animated = spotifyUrl,
                )
              }

              AppleMusicCanvasProvider.getBySongArtist(
                  song = song,
                  artist = artist,
                  album = albumName,
                  storefront = storefront,
                )
                ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                ?: TidalCanvasProvider.getBySongArtist(
                    song = song,
                    artist = artist,
                    album = albumName,
                  )
                  ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
            }

        // Only a real canvas: without one the cover stays. A video of the song from YouTube
        // was most often its still cover or an audio upload, zoomed in and softer.
        genuineCanvas
      }

    val validated = fetched?.let { artwork ->
      if (artwork.isYouTubeSnippet) return@let artwork
      val resultArtist = artwork.artist
      val artistMatches =
        if (resultArtist != null && artistNameRaw.isNotBlank()) {
          val normalizedResult = normalizeCanvasArtistName(resultArtist)
          val normalizedRequested = normalizeCanvasArtistName(artistNameRaw)
          resultArtist.contains(artistNameRaw, ignoreCase = true) ||
            artistNameRaw.contains(resultArtist, ignoreCase = true) ||
            normalizedResult.contains(normalizedRequested, ignoreCase = true) ||
            normalizedRequested.contains(normalizedResult, ignoreCase = true)
        } else {
          true
        }

      val canvasAlbumName = artwork.albumName
      val canvasSongName = artwork.name
      val titleMatches =
        when {
          canvasAlbumName != null && albumName.isNotBlank() ->
            canvasAlbumName.contains(albumName, ignoreCase = true) ||
              albumName.contains(canvasAlbumName, ignoreCase = true)
          canvasSongName != null && songTitleRaw.isNotBlank() ->
            canvasSongName.contains(songTitleRaw, ignoreCase = true) ||
              songTitleRaw.contains(canvasSongName, ignoreCase = true)
          else -> true
        }

      if (artistMatches && titleMatches) artwork else null
    }

    canvasArtwork = validated
    validated?.let { CanvasArtworkPlaybackCache.put(metadata.id, it) }
  }

  if (enableCanvas && !isEpisode) {
    canvasArtwork?.let { artwork ->
      CanvasArtworkPlayer(
        primaryUrl = artwork.animated,
        fallbackUrl = artwork.videoUrl,
        isPlaying = isPlaying,
        modifier = modifier,
      )
    }
  }
}
