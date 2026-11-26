package com.revenge.music.listentogether

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPolicyTest {

  @Test
  fun suppressionWindowCoversSentAndAppliedActions() {
    val window = SuppressionWindow(sentWindowMs = 300, appliedWindowMs = 200)

    window.markSent(1_000)
    assertTrue(window.isSuppressed(1_000))
    assertTrue(window.isSuppressed(1_299))
    assertFalse(window.isSuppressed(1_300))

    window.markApplied(2_000)
    assertTrue(window.isSuppressed(2_199))
    assertFalse(window.isSuppressed(2_200))

    // A fresh sent action suppresses again even after the applied window expired.
    window.markSent(2_100)
    assertTrue(window.isSuppressed(2_100))
  }

  @Test
  fun changeDebouncerWaitsForQuietWindow() {
    val debouncer = ChangeDebouncer<List<String>>(windowMs = 500)
    debouncer.seed(listOf("a"))

    debouncer.offer(listOf("a"), 0)
    assertNull(debouncer.ready(500))

    debouncer.offer(listOf("a", "b"), 100)
    assertNull(debouncer.ready(599))
    assertEquals(listOf("a", "b"), debouncer.ready(600))
    assertNull(debouncer.ready(601))

    // Repeating the same value is not a change.
    debouncer.offer(listOf("a", "b"), 1_000)
    assertNull(debouncer.ready(1_500))

    // A rapid second change restarts the window.
    debouncer.offer(listOf("a", "b", "c"), 1_100)
    debouncer.offer(listOf("a", "b", "c", "d"), 1_200)
    assertNull(debouncer.ready(1_650))
    assertEquals(listOf("a", "b", "c", "d"), debouncer.ready(1_700))
  }
}
