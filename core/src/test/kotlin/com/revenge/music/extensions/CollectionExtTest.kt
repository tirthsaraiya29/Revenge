package com.revenge.music.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CollectionExtTest {

  private enum class Sample {
    ALPHA,
    BETA,
  }

  @Test
  fun `reversed respects the flag without copying`() {
    val list = listOf(1, 2, 3)
    assertEquals(listOf(3, 2, 1), list.reversed(true))
    assertEquals(listOf(1, 2, 3), list.reversed(false))
  }

  @Test
  fun `move reorders in place`() {
    assertEquals(listOf(1, 3, 2), mutableListOf(1, 2, 3).move(1, 2))
    assertEquals(listOf(3, 1, 2), mutableListOf(1, 2, 3).move(2, 0))
  }

  @Test
  fun `mergeNearbyElements folds only adjacent matches`() {
    val values = listOf("a1", "a2", "b1", "a3")
    val merged =
      values.mergeNearbyElements(
        key = { it.first() },
        merge = { first, second -> first + second.drop(1) },
      )
    assertEquals(listOf("a12", "b1", "a3"), merged)
  }

  @Test
  fun `mergeNearbyElements keeps first element by default`() {
    assertEquals(listOf(1, 2, 3), listOf(1, 2, 3).mergeNearbyElements())
    assertEquals(emptyList<Int>(), emptyList<Int>().mergeNearbyElements())
  }

  @Test
  fun `toEnum parses names and falls back`() {
    assertEquals(Sample.BETA, "BETA".toEnum(Sample.ALPHA))
    assertEquals(Sample.ALPHA, "nope".toEnum(Sample.ALPHA))
    assertEquals(Sample.ALPHA, null.toEnum(Sample.ALPHA))
  }

  @Test
  fun `tryOrNull swallows exceptions`() {
    assertEquals(2, tryOrNull { 1 + 1 })
    assertNull(tryOrNull<Int> { error("boom") })
  }
}
