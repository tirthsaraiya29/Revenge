package com.revenge.playback.queues

import com.revenge.innertube.models.SongItem

internal class RadioQueuePageFilter {

  private val emitted = HashSet<String>()

  fun reset() {
    emitted.clear()
  }

  fun filter(songs: List<SongItem>): List<SongItem> {
    val result = ArrayList<SongItem>(songs.size)
    for (song in songs) {
      if (emitted.add(song.id)) result.add(song)
    }
    return result
  }
}
