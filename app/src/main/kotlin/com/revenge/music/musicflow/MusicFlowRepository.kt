package com.revenge.music.musicflow

import android.content.Context
import com.revenge.core.constants.DataSaverEnabledKey
import com.revenge.core.constants.HideExplicitKey
import com.revenge.core.constants.HideVideoSongsKey
import com.revenge.core.constants.MusicFlowHideRecentKey
import com.revenge.core.db.MusicDatabase
import com.revenge.core.db.entities.Song
import com.revenge.core.utils.dataStore
import com.revenge.innertube.YouTube
import com.revenge.innertube.models.Album
import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.models.WatchEndpoint
import com.revenge.innertube.models.filterBlockedArtists
import com.revenge.innertube.models.filterDislikedSongs
import com.revenge.innertube.models.filterExplicit
import com.revenge.innertube.models.filterVideoSongs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class MusicFlowOfflineException(message: String = "Music Flow is offline") : Exception(message)

/** Platform-neutral feed source so the session is testable without InnerTube. */
interface MusicFlowFeedSource {
  suspend fun signals(): MusicFlowSignals

  fun open(nowPlaying: MusicFlowItem?): MusicFlowCursor

  suspend fun offlineCandidates(): List<MusicFlowItem>
}

interface MusicFlowCursor {
  val hasMore: Boolean

  suspend fun nextPage(): List<MusicFlowItem>
}

@Singleton
class MusicFlowRepository
@Inject
constructor(
  private val database: MusicDatabase,
  @ApplicationContext private val context: Context,
) : MusicFlowFeedSource {

  override suspend fun signals(): MusicFlowSignals {
    val prefs = runCatching { context.dataStore.data.first() }.getOrDefault(emptyPreferences())
    val hideRecent = prefs[MusicFlowHideRecentKey] ?: true
    val hideExplicit = prefs[HideExplicitKey] ?: false
    val dataSaver = prefs[DataSaverEnabledKey] ?: false
    val hideVideoSongs = (prefs[HideVideoSongsKey] ?: false) || dataSaver

    val liked = runCatching {
      database.likedSongsByCreateDateAsc().first()
    }
      .getOrDefault(emptyList())
    val likedArtists = liked.flatMap { song -> song.artists }.mapNotNull { it.id }.toSet()
    val excluded = runCatching {
      database.recommendationExclusionDao.getAll().map { it.trackKey }
    }
      .getOrDefault(emptyList())
      .toSet()
    val hidden = runCatching {
      database.hiddenFromQuickPicksSongIds().first()
    }
      .getOrDefault(emptyList())
      .toSet()
    val recent =
      if (hideRecent) {
        runCatching { database.recentlyPlayedSongs(30).first() }
          .getOrDefault(emptyList())
          .map { it.song.id }
          .take(30)
          .toSet()
      } else {
        emptySet()
      }

    return MusicFlowSignals(
      likedArtistKeys = likedArtists,
      excludedTrackKeys = excluded,
      hiddenSongIds = hidden,
      recentSongIds = recent,
      hideExplicit = hideExplicit,
      hideVideoSongs = hideVideoSongs,
    )
  }

  override fun open(nowPlaying: MusicFlowItem?): MusicFlowCursor = RadioCursor(nowPlaying)

  override suspend fun offlineCandidates(): List<MusicFlowItem> {
    val songs = runCatching {
      database.downloadedSongsByCreateDateAsc().first()
    }
      .getOrDefault(emptyList())
    return songs.shuffled().map { it.toFeedItem(MusicFlowSource.OFFLINE) }
  }

  private fun emptyPreferences() = androidx.datastore.preferences.core.emptyPreferences()

  private inner class RadioCursor(private val nowPlaying: MusicFlowItem?) : MusicFlowCursor {
    private val seedQueue = ArrayDeque<MusicFlowItem>()
    private var tailContinuation: String? = null
    private var tailEndpoint: WatchEndpoint? = null
    private var networkFailure = false
    private var initialised = false

    init {
      nowPlaying?.let { seedQueue.addLast(it) }
    }

    override val hasMore: Boolean
      get() = !initialised || seedQueue.isNotEmpty() || tailContinuation != null

    override suspend fun nextPage(): List<MusicFlowItem> {
      if (!initialised) {
        initialised = true
        seedQueue.addAll(buildSeedPool(nowPlaying))
      }
      networkFailure = false
      if (seedQueue.isNotEmpty()) {
        val batch = ArrayList<MusicFlowItem>(2)
        repeat(SEEDS_PER_BATCH) {
          seedQueue.removeFirstOrNull()?.let(batch::add)
        }
        val expanded = expand(batch)
        if (expanded.isNotEmpty()) {
          return interleave(expanded.map { it.second })
        }
      }
      val continuation = tailContinuation
      val endpoint = tailEndpoint
      if (continuation != null && endpoint != null) {
        val page = YouTube.next(endpoint, continuation)
        if (page.isFailure) networkFailure = true
        val next = page.getOrNull()
        tailContinuation = next?.continuation
        tailEndpoint = next?.endpoint ?: endpoint
        val items = filter(next?.items.orEmpty(), MusicFlowSource.CATALOG)
        if (items.isNotEmpty()) return items
      }
      if (networkFailure) throw MusicFlowOfflineException()
      return emptyList()
    }

    private suspend fun buildSeedPool(nowPlaying: MusicFlowItem?): List<MusicFlowItem> {
      val recent = runCatching {
        database.recentlyPlayedSongs(SEED_LIMIT).first()
      }
        .getOrDefault(emptyList())
        .take(SEED_LIMIT)
        .map { it.toFeedItem(MusicFlowSource.HISTORY) }
      val liked = runCatching {
        database.likedSongsByCreateDateAsc().first()
      }
        .getOrDefault(emptyList())
        .takeLast(LIKED_POOL)
        .shuffled()
        .take(SEED_LIMIT)
        .map { it.toFeedItem(MusicFlowSource.LIKED) }
      val quickPicks = runCatching {
        database.quickPicks().first()
      }
        .getOrDefault(emptyList())
        .take(SEED_LIMIT)
        .map { it.toFeedItem(MusicFlowSource.QUICK_PICKS) }

      val interleaved = interleave(listOf(recent, liked, quickPicks))
      if (interleaved.isNotEmpty()) {
        return interleaved.distinctBy { it.songId }
      }
      val home = runCatching { YouTube.home().getOrNull() }.getOrNull()
      val songs = home?.sections.orEmpty().flatMap { it.items }.filterIsInstance<SongItem>()
      return songs.map { MusicFlowItem(it, MusicFlowSource.CATALOG) }.distinctBy { it.songId }
    }

    private suspend fun expand(
      seeds: List<MusicFlowItem>,
    ): List<Pair<MusicFlowItem, List<MusicFlowItem>>> {
      val result = ArrayList<Pair<MusicFlowItem, List<MusicFlowItem>>>()
      for (seed in seeds) {
        val endpoint =
          WatchEndpoint(
            videoId = seed.songId,
            playlistId = "RDAMVM${seed.songId}",
          )
        val page = YouTube.next(endpoint)
        if (page.isFailure) {
          networkFailure = true
          continue
        }
        val next = page.getOrNull() ?: continue
        if (next.continuation != null) {
          tailContinuation = next.continuation
          tailEndpoint = next.endpoint
        }
        val filtered = filter(next.items, seed.source)
        if (filtered.isNotEmpty()) result += seed to filtered
      }
      return result
    }

    private fun filter(items: List<SongItem>, source: MusicFlowSource): List<MusicFlowItem> {
      val signals = currentSignals
      return items
        .filterExplicit(signals.hideExplicit)
        .filterVideoSongs(signals.hideVideoSongs)
        .filterBlockedArtists(signals.blockedArtists)
        .filterDislikedSongs(signals.dislikedSongs)
        .filter {
          trackKeyOf(it.title, it.artists.joinToString(", ") { a -> a.name }) !in
            signals.excludedTrackKeys
        }
        .map { MusicFlowItem(it, source) }
    }

    private val currentSignals: MusicFlowSignals by lazy {
      kotlinx.coroutines.runBlocking { signals() }
    }

    private fun interleave(groups: List<List<MusicFlowItem>>): List<MusicFlowItem> {
      val queues = groups.map { ArrayDeque(it) }
      val out = ArrayList<MusicFlowItem>()
      var added = true
      while (added) {
        added = false
        for (queue in queues) {
          queue.removeFirstOrNull()?.let {
            out += it
            added = true
          }
        }
      }
      return out
    }
  }

  companion object {
    const val SEEDS_PER_BATCH = 2
    const val SEED_LIMIT = 6
    const val LIKED_POOL = 40
    const val RECENT_EXCLUSION = 30
  }
}

internal fun Song.toFeedItem(source: MusicFlowSource): MusicFlowItem {
  val entity = song
  return MusicFlowItem(
    song =
      SongItem(
        id = entity.id,
        title = entity.title,
        artists = artists.map { Artist(it.name, it.id) },
        album =
          album?.let { Album(it.title, it.id) }
            ?: entity.albumName?.let { Album(it, entity.albumId.orEmpty()) },
        duration = entity.duration.takeIf { it > 0 },
        thumbnail = entity.thumbnailUrl.orEmpty(),
        explicit = entity.explicit,
        musicVideoType = if (entity.isVideo) "MUSIC_VIDEO_TYPE_OMV" else "MUSIC_VIDEO_TYPE_ATV",
      ),
    source = source,
  )
}
