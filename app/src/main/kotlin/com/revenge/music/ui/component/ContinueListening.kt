package com.revenge.music.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.revenge.core.extensions.toMediaItem
import com.revenge.core.models.toMediaMetadata
import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_PODCAST_EPISODE
import com.revenge.music.LocalPlayerConnection
import com.revenge.music.R
import com.revenge.music.utils.EpisodeProgress
import com.revenge.music.utils.PodcastProgress
import com.revenge.music.utils.listItemShape
import com.revenge.playback.queues.ListQueue

/** Episodes started and not finished, most recently listened first. */
@Composable
fun rememberEpisodesInProgress(limit: Int = Int.MAX_VALUE): List<EpisodeProgress> {
  val context = LocalContext.current
  val progress = remember { PodcastProgress.getInstance(context) }
  val entries by progress.entries.collectAsState()
  return progress.inProgress(entries).take(limit)
}

/** An episode as a [SongItem], so it is drawn and played like the episodes of a show. */
fun EpisodeProgress.toSongItem() =
  SongItem(
    id = id,
    title = title,
    artists = listOfNotNull(show?.let { Artist(name = it, id = showBrowseId) }),
    duration = (durationMs / 1000).toInt().takeIf { it > 0 },
    musicVideoType = MUSIC_VIDEO_TYPE_PODCAST_EPISODE,
    thumbnail = thumbnailUrl.orEmpty(),
  )

/**
 * "Continue listening": a title and one row per episode in [episodes]; tapping one continues it
 * where it was left.
 */
fun LazyListScope.continueListening(episodes: List<EpisodeProgress>, keyPrefix: String) {
  if (episodes.isEmpty()) return
  item(key = "${keyPrefix}_continue_listening_title") {
    NavigationTitle(title = stringResource(R.string.continue_listening))
  }
  itemsIndexed(episodes, key = { _, it -> "${keyPrefix}_continue_listening_${it.id}" }) {
    index,
    episode ->
    val playerConnection = LocalPlayerConnection.current
    val context = LocalContext.current
    val song = episode.toSongItem()
    YouTubeListItem(
      item = song,
      isSwipeable = false,
      shape = listItemShape(index = index, count = episodes.size),
      trailingContent = {
        IconButton(onClick = { PodcastProgress.getInstance(context).dismiss(episode.id) }) {
          Icon(
            painter = painterResource(R.drawable.close),
            contentDescription = stringResource(R.string.remove_from_continue_listening),
          )
        }
      },
      modifier =
        Modifier.clickable {
          playerConnection?.playQueue(
            ListQueue(
              title = episode.show,
              items = listOf(song.toMediaMetadata().toMediaItem()),
              position = episode.positionMs,
            ),
          )
        },
    )
  }
}
