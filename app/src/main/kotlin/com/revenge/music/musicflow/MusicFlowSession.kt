package com.revenge.music.musicflow

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Append-only, paged feed. Every operation is serialised; a generation counter identifies the live
 * feed so queues created by an older [reset] stop paging.
 */
@Singleton
class MusicFlowSession
@Inject
constructor(
  private val source: MusicFlowFeedSource,
) {
  companion object {
    const val INITIAL_TARGET = 12
    const val MAX_FETCHES_PER_LOAD = 3
    const val MAX_FEED_SIZE = 300
    const val PREFETCH_THRESHOLD = 4
  }

  private val mutex = Mutex()
  private val _state = MutableStateFlow(MusicFlowFeedState())
  val state: StateFlow<MusicFlowFeedState> = _state.asStateFlow()

  private var started = false
  private var generation = 0L
  private var cursor: MusicFlowCursor? = null
  private var signals = MusicFlowSignals()
  private val seenSongIds = HashSet<String>()
  private val seenTrackKeys = HashSet<String>()
  private val hiddenSongIds = HashSet<String>()
  private val lessLikeThisArtists = HashSet<String>()
  private val tailArtists = ArrayList<String>()
  private var offline = false

  val currentGeneration: Long
    get() = generation

  val hasMore: Boolean
    get() = _state.value.hasMore

  fun isLiveGeneration(candidate: Long): Boolean = candidate == generation

  data class QueueHandle(
    val generation: Long,
    val items: List<MusicFlowItem>,
    val selectedIndex: Int,
  )

  fun queueHandle(): QueueHandle =
    QueueHandle(
      generation = generation,
      items = _state.value.items,
      selectedIndex = _state.value.selectedIndex,
    )

  fun snapshot(): MusicFlowFeedState = _state.value

  suspend fun start(nowPlaying: MusicFlowItem?) {
    mutex.withLock {
      if (started) return
      load(nowPlaying)
    }
  }

  suspend fun reset(nowPlaying: MusicFlowItem?) {
    mutex.withLock { load(nowPlaying) }
  }

  suspend fun loadMore(): List<MusicFlowItem> {
    mutex.withLock {
      val snapshot = _state.value
      if (
        !started ||
          snapshot.isLoadingMore ||
          !snapshot.hasMore ||
          snapshot.isOffline ||
          snapshot.items.size >= MAX_FEED_SIZE
      ) {
        return emptyList()
      }
      val startSize = snapshot.items.size
      _state.value = snapshot.copy(isLoadingMore = true)
      var added = 0
      var rounds = 0
      while (added == 0 && rounds < MAX_FETCHES_PER_LOAD) {
        rounds++
        if (cursor == null) break
        try {
          added = fetchPage()
        } catch (offlineError: MusicFlowOfflineException) {
          offline = true
          _state.value = _state.value.copy(error = MusicFlowFeedError.OFFLINE, isOffline = true)
          break
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
          throw cancelled
        } catch (t: Throwable) {
          _state.value = _state.value.copy(error = MusicFlowFeedError.FAILED)
          break
        }
      }
      val updated = _state.value
      _state.value =
        updated.copy(
          isLoadingMore = false,
          hasMore = cursor?.hasMore == true && !offline,
          error = if (added > 0) null else updated.error,
        )
      return _state.value.items.drop(startSize)
    }
  }

  fun select(index: Int) {
    val items = _state.value.items
    val clamped = if (items.isEmpty()) 0 else index.coerceIn(0, items.lastIndex)
    _state.value = _state.value.copy(selectedIndex = clamped)
  }

  fun indexOf(songId: String): Int = _state.value.items.indexOfFirst { it.songId == songId }

  fun hide(songId: String) {
    hiddenSongIds += songId
    val items = _state.value.items
    val removal = items.indexOfFirst { it.songId == songId }
    if (removal < 0) return
    val selected = _state.value.selectedIndex
    val nextItems = items.filterIndexed { index, _ -> index != removal }
    val nextSelected =
      when {
        nextItems.isEmpty() -> 0
        selected > removal -> selected - 1
        else -> selected.coerceAtMost(nextItems.lastIndex)
      }
    _state.value = _state.value.copy(items = nextItems, selectedIndex = nextSelected)
  }

  fun lessLikeThis(item: MusicFlowItem) {
    item.primaryArtistKey?.let { lessLikeThisArtists += it }
    hide(item.songId)
  }

  fun isHidden(songId: String): Boolean = songId in hiddenSongIds

  fun toUiState(
    playbackStatus: MusicFlowPlaybackStatus = MusicFlowPlaybackStatus.Idle,
    canRestorePreviousQueue: Boolean = false,
  ): MusicFlowUiState {
    val current = _state.value
    return MusicFlowUiState(
      items = current.items,
      selectedIndex = current.selectedIndex,
      isInitialLoading = current.isInitialLoading,
      isLoadingMore = current.isLoadingMore,
      error = current.error,
      hasMore = current.hasMore,
      playbackStatus = playbackStatus,
      canRestorePreviousQueue = canRestorePreviousQueue,
      isOfflineFeed = current.isOffline,
    )
  }

  // ------------------------------------------------------------------ internals

  private suspend fun load(nowPlaying: MusicFlowItem?) {
    generation++
    started = true
    cursor = null
    seenSongIds.clear()
    seenTrackKeys.clear()
    hiddenSongIds.clear()
    lessLikeThisArtists.clear()
    tailArtists.clear()
    offline = false

    val seedItems = nowPlaying?.let { listOf(it) }.orEmpty()
    seedItems.forEach(::markSeen)
    _state.value =
      MusicFlowFeedState(
        items = seedItems,
        selectedIndex = 0,
        isInitialLoading = true,
        hasMore = true,
      )

    signals = runCatching { source.signals() }.getOrDefault(MusicFlowSignals())
    cursor = runCatching { source.open(nowPlaying) }.getOrNull()

    var fetchError: MusicFlowFeedError? = null
    var rounds = 0
    while (
      _state.value.items.size < INITIAL_TARGET && rounds < MAX_FETCHES_PER_LOAD && cursor != null
    ) {
      rounds++
      try {
        fetchPage()
      } catch (offlineError: MusicFlowOfflineException) {
        offline = true
        fetchError = MusicFlowFeedError.OFFLINE
        if (_state.value.items.size <= seedItems.size) {
          appendOffline()
        }
        break
      } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
      } catch (t: Throwable) {
        fetchError = MusicFlowFeedError.FAILED
        break
      }
    }

    val items = _state.value.items
    val error =
      when {
        offline -> MusicFlowFeedError.OFFLINE
        items.isEmpty() && fetchError == null -> MusicFlowFeedError.EMPTY
        else -> fetchError
      }
    _state.value =
      _state.value.copy(
        isInitialLoading = false,
        error = error,
        isOffline = offline,
        hasMore = cursor?.hasMore == true && !offline,
      )
  }

  private suspend fun fetchPage(): Int {
    val activeCursor = cursor ?: return 0
    val page = activeCursor.nextPage()
    return append(page)
  }

  private suspend fun appendOffline() {
    val candidates = runCatching { source.offlineCandidates() }.getOrDefault(emptyList())
    append(candidates)
    _state.value = _state.value.copy(isOffline = true)
  }

  private fun append(candidates: List<MusicFlowItem>): Int {
    if (candidates.isEmpty()) return 0
    val filter =
      MusicFlowCandidateFilter(
        seenSongIds = seenSongIds,
        seenTrackKeys = seenTrackKeys,
        excludedTrackKeys = signals.excludedTrackKeys,
        hiddenSongIds = hiddenSongIds + signals.hiddenSongIds,
        recentSongIds = signals.recentSongIds,
      )
    val filtered =
      filterMusicFlowCandidates(candidates, filter).filterNot { it.songId in hiddenSongIds }
    val ranked =
      rankMusicFlowCandidates(
        items = filtered,
        likedArtistKeys = signals.likedArtistKeys,
        lessLikeThisArtistKeys = lessLikeThisArtists,
        feedTailArtists = tailArtists,
      )
    val room = (MAX_FEED_SIZE - _state.value.items.size).coerceAtLeast(0)
    val admitted = ranked.take(room)
    if (admitted.isEmpty()) return 0
    admitted.forEach(::markSeen)
    _state.value =
      _state.value.copy(
        items = _state.value.items + admitted,
        error = null,
      )
    return admitted.size
  }

  private fun markSeen(item: MusicFlowItem) {
    seenSongIds += item.songId
    seenTrackKeys += trackKeyOf(item)
    item.primaryArtistKey?.let { artist ->
      tailArtists += artist
      while (tailArtists.size > 2) tailArtists.removeAt(0)
    }
  }
}
