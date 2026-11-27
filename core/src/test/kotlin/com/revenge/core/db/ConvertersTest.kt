package com.revenge.core.db

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConvertersTest {
  private val converters = Converters()

  @Test
  fun roundTripLeapDay() {
    val value = LocalDateTime.of(2024, 2, 29, 13, 45, 7, 123_000_000)
    assertEquals(value, converters.fromTimestamp(converters.dateToTimestamp(value)))
  }

  @Test
  fun roundTripEpoch() {
    val value = LocalDateTime.of(1970, 1, 1, 0, 0, 0, 0)
    assertEquals(0L, converters.dateToTimestamp(value))
    assertEquals(value, converters.fromTimestamp(0L))
  }

  @Test
  fun roundTripLastMillisecondOfYear() {
    val value = LocalDateTime.of(2024, 12, 31, 23, 59, 59, 999_000_000)
    assertEquals(value, converters.fromTimestamp(converters.dateToTimestamp(value)))
  }

  @Test
  fun utcInterpretationIsNotSystemZoneDependent() {
    val value = LocalDateTime.of(2020, 6, 15, 6, 30, 15, 250_000_000)
    val timestamp = converters.dateToTimestamp(value)
    assertEquals(java.time.Instant.parse("2020-06-15T06:30:15.250Z").toEpochMilli(), timestamp)
    assertEquals(value, converters.fromTimestamp(timestamp))
  }

  @Test
  fun nullsRemainNull() {
    assertNull(converters.fromTimestamp(null))
    assertNull(converters.dateToTimestamp(null))
  }
}
