package com.revenge.music.db

import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConvertersTest {
  private val converters = Converters()

  @Test
  fun `timestamps round-trip through UTC epoch millis`() {
    val original = LocalDateTime.of(2024, 2, 29, 13, 45, 7, 123_000_000)
    val stored = converters.dateToTimestamp(original)
    val restored = converters.fromTimestamp(stored)

    assertEquals(original, restored)
    assertEquals(
      original.atZone(ZoneOffset.UTC).toInstant().toEpochMilli(),
      stored,
    )
  }

  @Test
  fun `null timestamps stay null`() {
    assertNull(converters.dateToTimestamp(null))
    assertNull(converters.fromTimestamp(null))
  }
}
