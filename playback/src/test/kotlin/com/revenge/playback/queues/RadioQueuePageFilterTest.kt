package com.revenge.playback.queues

import com.revenge.innertube.models.SongItem
import org.junit.Assert.assertEquals
import org.junit.Test

class RadioQueuePageFilterTest {

  @Test
  fun duplicateIdsWithinOnePageCollapseToFirst() {
    val filter = RadioQueuePageFilter()

    val result = filter.filter(listOf(song("a"), song("b"), song("a"), song("c"), song("b")))

    assertEquals(listOf("a", "b", "c"), result.map { it.id })
  }

  @Test
  fun previouslyEmittedIdsAreNeverRepeated() {
    val filter = RadioQueuePageFilter()
    filter.filter(listOf(song("a"), song("b")))

    val result = filter.filter(listOf(song("b"), song("c"), song("a"), song("d")))

    assertEquals(listOf("c", "d"), result.map { it.id })
  }

  @Test
  fun resetStartsANewSession() {
    val filter = RadioQueuePageFilter()
    filter.filter(listOf(song("a"), song("b")))

    filter.reset()
    val result = filter.filter(listOf(song("a"), song("b")))

    assertEquals(listOf("a", "b"), result.map { it.id })
  }

  private fun song(id: String): SongItem =
    SongItem(
      id = id,
      title = id,
      artists = emptyList(),
      thumbnail = "",
    )
}
