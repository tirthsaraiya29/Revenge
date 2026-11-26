package com.revenge.music.utils

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Best-effort parser for podcast episode date labels. */
object EpisodeDates {
    private const val DAY_MS = 86_400_000L

    private val monthNames: Map<String, Int> by lazy {
        val map = HashMap<String, Int>()
        val locales = listOf(Locale.getDefault(), Locale.ENGLISH)
        for (locale in locales) {
            for (month in 1..12) {
                val full = java.time.Month.of(month)
                    .getDisplayName(java.time.format.TextStyle.FULL, locale)
                    .lowercase(locale)
                val short = java.time.Month.of(month)
                    .getDisplayName(java.time.format.TextStyle.SHORT, locale)
                    .lowercase(locale)
                    .trimEnd('.')
                map.putIfAbsent(full, month)
                map.putIfAbsent(short, month)
            }
        }
        map
    }

    private val dmy = Regex("""(\d{1,2})\s+([\p{L}.]+)(?:\s+(\d{4}))?""")
    private val mdy = Regex("""([\p{L}.]+)\s+(\d{1,2})(?:,?\s+(\d{4}))?""")
    private val relative = Regex("""(\d+)\s*([\p{L}]+)""")

    fun parse(text: String, now: Long = System.currentTimeMillis()): Long? {
        val value = text.trim().lowercase(Locale.ROOT)
        if (value.isEmpty()) return null
        if (value == "today" || value == "oggi") return startOfDay(now)
        if (value == "yesterday" || value == "ieri") return startOfDay(now) - DAY_MS

        dmy.find(value)?.let { match ->
            val day = match.groupValues[1].toIntOrNull()
            val month = monthOf(match.groupValues[2])
            val year = match.groupValues[3].takeIf { it.isNotBlank() }?.toIntOrNull()
            if (day != null && month != null) {
                return absolute(day, month, year, now)
            }
        }
        mdy.find(value)?.let { match ->
            val month = monthOf(match.groupValues[1])
            val day = match.groupValues[2].toIntOrNull()
            val year = match.groupValues[3].takeIf { it.isNotBlank() }?.toIntOrNull()
            if (day != null && month != null) {
                return absolute(day, month, year, now)
            }
        }
        relative.find(value)?.let { match ->
            val amount = match.groupValues[1].toLongOrNull() ?: return null
            val unit = match.groupValues[2]
            val millis = unitMillis(unit) ?: return null
            return now - amount * millis
        }
        return null
    }

    private fun monthOf(raw: String): Int? =
        monthNames[raw.trim().trimEnd('.').lowercase(Locale.ROOT)]

    private fun absolute(day: Int, month: Int, year: Int?, now: Long): Long? {
        val zone = ZoneId.systemDefault()
        val current = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return runCatching {
            var date = LocalDate.of(year ?: current.year, month, day)
            if (year == null && date.isAfter(current)) {
                date = date.minusYears(1)
            }
            date.atStartOfDay(zone).toInstant().toEpochMilli()
        }.getOrNull()
    }

    private fun startOfDay(now: Long): Long = Instant.ofEpochMilli(now)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    private fun unitMillis(unit: String): Long? = when (unit.lowercase(Locale.ROOT)) {
        "second", "seconds", "secondo", "secondi", "sec", "secs" -> 1_000L
        "minute", "minutes", "minuto", "minuti", "min" -> 60_000L
        "hour", "hours", "ora", "ore" -> 3_600_000L
        "day", "days", "giorno", "giorni" -> DAY_MS
        "week", "weeks", "settimana", "settimane" -> 7 * DAY_MS
        "month", "months", "mese", "mesi" -> 30 * DAY_MS
        "year", "years", "anno", "anni" -> 365 * DAY_MS
        else -> null
    }
}
