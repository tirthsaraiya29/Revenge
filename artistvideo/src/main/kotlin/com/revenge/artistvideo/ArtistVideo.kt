package com.revenge.artistvideo

import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import okhttp3.OkHttpClient

private const val MIME_HLS = "application/x-mpegURL"
private const val MIME_MP4 = "video/mp4"

/** Substring-based classification: anything containing "mp4" is MP4, everything else HLS. */
internal fun videoMimeType(url: String): String =
  if (url.lowercase().contains("mp4")) MIME_MP4 else MIME_HLS

/**
 * Muted looping artwork surface. Fades in on the first rendered frame; the player lives for the
 * composition and is re-pointed when [videoUrl] changes.
 */
@Composable
fun ArtistVideo(
  videoUrl: String,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  val context = LocalContext.current

  val player = remember {
    ExoPlayer.Builder(context)
      .setMediaSourceFactory(
        DefaultMediaSourceFactory(
          DefaultDataSource.Factory(context, OkHttpDataSource.Factory(OkHttpClient())),
        ),
      )
      .build()
      .apply {
        volume = 0f
        repeatMode = Player.REPEAT_MODE_ONE
        setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build(),
          false,
        )
      }
  }

  var firstFrameRendered by remember { mutableStateOf(false) }
  val surfaceAlpha by
    animateFloatAsState(
      targetValue = if (firstFrameRendered) 1f else 0f,
      animationSpec = tween(durationMillis = 400),
      label = "artistVideoAlpha",
    )

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

  LaunchedEffect(videoUrl) {
    firstFrameRendered = false
    val target = videoUrl.trim()
    if (target.isEmpty()) {
      player.stop()
      player.clearMediaItems()
    } else {
      player.setMediaItem(
        MediaItem.Builder().setUri(target).setMimeType(videoMimeType(target)).build(),
      )
      player.prepare()
      player.play()
    }
  }

  Box(
    modifier =
      modifier
        .clip(RoundedCornerShape(4.dp))
        .background(Color.Black.copy(alpha = 0.30f))
        .clickable { onClick() },
  ) {
    AndroidView(
      factory = { viewContext ->
        AspectRatioFrameLayout(viewContext).apply {
          resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
          addView(
            TextureView(viewContext).apply {
              layoutParams =
                FrameLayout.LayoutParams(
                  ViewGroup.LayoutParams.MATCH_PARENT,
                  ViewGroup.LayoutParams.MATCH_PARENT,
                )
            },
          )
        }
      },
      modifier = Modifier.fillMaxSize().graphicsLayer { alpha = surfaceAlpha },
      update = { frame ->
        (frame.getChildAt(0) as? TextureView)?.let { player.setVideoTextureView(it) }
      },
    )
  }
}
