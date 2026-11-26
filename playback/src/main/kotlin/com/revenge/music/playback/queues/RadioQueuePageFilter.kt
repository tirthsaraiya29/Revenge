package com.revenge.music.playback.queues

import com.revenge.innertube.models.SongItem

/** Removes songs already handed out by this radio session so pages never duplicate tracks. */
internal class RadioQueuePageFilter {
  private val emittedIds = HashSet<String>()

  fun reset() {
    emittedIds.clear()
  }

  fun filter(songs: List<SongItem>): List<SongItem> = songs.filter { emittedIds.add(it.id) }
}
