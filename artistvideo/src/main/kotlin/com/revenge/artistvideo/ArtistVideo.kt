package com.revenge.artistvideo

import android.graphics.Color as AndroidColor
import android.view.TextureView
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import java.util.Locale
import okhttp3.OkHttpClient

/**
 * Muted, looping video surface used for artist backgrounds.
 *
 * The player is created once per composition and reused when [videoUrl] changes; the URL is
 * simply swapped as the current media item. The surface stays invisible until the first frame is
 * rendered, then fades in so callers see the poster/background beneath it in the meantime.
 */
@Composable
fun ArtistVideo(
  videoUrl: String,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  val context = LocalContext.current
  var firstFrameRendered by remember { mutableStateOf(false) }

  val okHttpClient = remember { OkHttpClient() }
  val dataSourceFactory =
    remember(context, okHttpClient) {
      DefaultDataSource.Factory(context, OkHttpDataSource.Factory(okHttpClient))
    }
  val mediaSourceFactory =
    remember(dataSourceFactory) { DefaultMediaSourceFactory(dataSourceFactory) }

  val player =
    remember(context, mediaSourceFactory) {
      ExoPlayer.Builder(context)
        .setMediaSourceFactory(mediaSourceFactory)
        .build()
        .apply {
          setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(C.USAGE_MEDIA)
              .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
              .build(),
            false,
          )
          volume = 0f
          repeatMode = Player.REPEAT_MODE_ONE
        }
    }

  DisposableEffect(player) {
    val listener =
      object : Player.Listener {
        override fun onRenderedFirstFrame() {
          firstFrameRendered = true
        }
      }
    player.addListener(listener)
    onDispose {
      player.removeListener(listener)
      player.release()
    }
  }

  LaunchedEffect(player, videoUrl) {
    val url = videoUrl.trim()
    firstFrameRendered = false
    if (url.isEmpty()) {
      player.stop()
      player.clearMediaItems()
      return@LaunchedEffect
    }
    player.setMediaItem(
      MediaItem.Builder()
        .setUri(url)
        .setMimeType(videoMimeType(url))
        .build()
    )
    player.prepare()
    player.playWhenReady = true
  }

  val surfaceAlpha by
    animateFloatAsState(
      targetValue = if (firstFrameRendered) 1f else 0f,
      animationSpec = tween(durationMillis = 400),
      label = "artistVideoAlpha",
    )

  Box(
    modifier =
      modifier
        .clip(RoundedCornerShape(4.dp))
        .background(Color.Black.copy(alpha = 0.3f))
        .clickable(onClick = onClick)
  ) {
    AndroidView(
      modifier = Modifier.matchParentSize().alpha(surfaceAlpha),
      factory = { viewContext ->
        AspectRatioFrameLayout(viewContext).apply {
          layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
          resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
          setBackgroundColor(AndroidColor.TRANSPARENT)
          val textureView =
            TextureView(viewContext).apply {
              layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            }
          addView(textureView)
          player.setVideoTextureView(textureView)
        }
      },
    )
  }
}

/** HLS is the default because the provider serves m3u8 artwork clips; plain mp4 is also accepted. */
internal fun videoMimeType(url: String): String {
  val normalized = url.lowercase(Locale.ROOT)
  return when {
    normalized.contains("mp4") -> MimeTypes.VIDEO_MP4
    else -> MimeTypes.APPLICATION_M3U8
  }
}

