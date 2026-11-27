package com.revenge.music.utils

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodeDatesContractTest {
  private val zone: ZoneId = ZoneId.systemDefault()
  private val now: Long =
    LocalDateTime.of(2026, 11, 27, 12, 0).atZone(zone).toInstant().toEpochMilli()

  private fun startOf(year: Int, month: Int, day: Int): Long =
    LocalDate.of(year, month, day).atStartOfDay(zone).toInstant().toEpochMilli()

  @Test
  fun parsesAbsoluteDayMonthYear() {
    assertEquals(startOf(2026, 11, 27), EpisodeDates.parse("27 Nov 2026", now))
  }

  @Test
  fun parsesAbsoluteMonthDayYear() {
    assertEquals(startOf(2026, 11, 27), EpisodeDates.parse("Nov 27, 2026", now))
  }

  @Test
  fun omitsYearAndRollsBackWhenFuture() {
    assertEquals(startOf(2026, 1, 5), EpisodeDates.parse("5 Jan", now))
    assertEquals(startOf(2025, 12, 30), EpisodeDates.parse("30 Dec", now))
  }

  @Test
  fun parsesEnglishRelativeUnits() {
    assertEquals(now - 2 * 3_600_000L, EpisodeDates.parse("2 hours ago", now))
    assertEquals(now - 3 * 86_400_000L, EpisodeDates.parse("3 days ago", now))
  }

  @Test
  fun parsesItalianRelativeUnits() {
    assertEquals(now - 3 * 86_400_000L, EpisodeDates.parse("3 giorni fa", now))
    assertEquals(now - 5 * 60_000L, EpisodeDates.parse("5 minuti fa", now))
  }

  @Test
  fun parsesTodayAndYesterday() {
    assertEquals(startOf(2026, 11, 27), EpisodeDates.parse("today", now))
    assertEquals(startOf(2026, 11, 26), EpisodeDates.parse("yesterday", now))
    assertEquals(startOf(2026, 11, 27), EpisodeDates.parse("oggi", now))
  }

  @Test
  fun returnsNullForUnrecognisedText() {
    assertNull(EpisodeDates.parse("", now))
    assertNull(EpisodeDates.parse("not a date at all", now))
  }
}
