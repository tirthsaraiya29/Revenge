package com.revenge.music.utils

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/**
 * Best-effort parser for podcast episode publication labels such as
 * `"Today"`, `"3 days ago"`, `"12 Mar"` or `"Mar 12, 2024"`.
 */
object EpisodeDates {

    private data class Unit(val english: List<String>, val italian: List<String>)

    private val UNITS = mapOf(
        "seconds" to Unit(listOf("second", "seconds", "sec", "secs"), listOf("secondo", "secondi")),
        "minutes" to Unit(listOf("minute", "minutes", "min", "mins"), listOf("minuto", "minuti")),
        "hours" to Unit(listOf("hour", "hours", "hr", "hrs"), listOf("ora", "ore")),
        "days" to Unit(listOf("day", "days"), listOf("giorno", "giorni")),
        "weeks" to Unit(listOf("week", "weeks"), listOf("settimana", "settimane")),
        "months" to Unit(listOf("month", "months"), listOf("mese", "mesi")),
        "years" to Unit(listOf("year", "years"), listOf("anno", "anni")),
    )

    fun parse(text: String?, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        val raw = text?.trim()?.lowercase(Locale.ROOT) ?: return null
        if (raw.isEmpty()) return null

        when (raw) {
            "today", "oggi" -> return now.toLocalDate().atStartOfDay()
            "yesterday", "ieri" -> return now.toLocalDate().minusDays(1).atStartOfDay()
        }

        relative(raw, now)?.let { return it }
        absolute(raw, now)?.let { return it }
        return null
    }

    private fun relative(text: String, now: LocalDateTime): LocalDateTime? {
        val match = RELATIVE.find(text) ?: return null
        val amount = match.groupValues[1].toLongOrNull() ?: return null
        val unitText = match.groupValues[2]
        val unit = UNITS.entries.firstOrNull { (_, unit) ->
            unit.english.any { it == unitText } || unit.italian.any { it == unitText }
        }?.key ?: return null

        return when (unit) {
            "seconds" -> now.minusSeconds(amount)
            "minutes" -> now.minusMinutes(amount)
            "hours" -> now.minusHours(amount)
            "days" -> now.minusDays(amount)
            "weeks" -> now.minusWeeks(amount)
            "months" -> now.minusMonths(amount)
            "years" -> now.minusYears(amount)
            else -> null
        }
    }

    private fun absolute(text: String, now: LocalDateTime): LocalDateTime? {
        val dayFirst = DAY_FIRST.find(text)?.let { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return@let null
            val month = month(match.groupValues[2]) ?: return@let null
            val year = match.groupValues[3].toIntOrNull()
            resolve(day, month, year, now)
        }
        if (dayFirst != null) return dayFirst.atStartOfDay()

        val monthFirst = MONTH_FIRST.find(text)?.let { match ->
            val month = month(match.groupValues[1]) ?: return@let null
            val day = match.groupValues[2].toIntOrNull() ?: return@let null
            val year = match.groupValues[3].toIntOrNull()
            resolve(day, month, year, now)
        }
        return monthFirst?.atStartOfDay()
    }

    private fun resolve(day: Int, month: Month, year: Int?, now: LocalDateTime): LocalDate? {
        return try {
            if (year != null) {
                LocalDate.of(year, month, day)
            } else {
                val candidate = LocalDate.of(now.year, month, day)
                if (candidate.isAfter(now.toLocalDate())) candidate.minusYears(1) else candidate
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun month(button: String): Month? {
        val name = button.trim().removeSuffix(".").lowercase(Locale.ROOT)
        if (name.isEmpty()) return null
        for (locale in listOf(Locale.getDefault(), Locale.ENGLISH)) {
            for (month in Month.entries) {
                val full = month.getDisplayName(TextStyle.FULL, locale).lowercase(Locale.ROOT)
                val short = month.getDisplayName(TextStyle.SHORT, locale).lowercase(Locale.ROOT)
                if (name == full || name == short || name == short.removeSuffix(".")) return month
            }
        }
        return null
    }

    private val RELATIVE = Regex("""(\d+)\s+([\p{L}]+)""")
    private val DAY_FIRST = Regex("""(\d{1,2})\s+([\p{L}.]+)(?:\s+(\d{4}))?""")
    private val MONTH_FIRST = Regex("""([\p{L}.]+)\s+(\d{1,2})(?:,?\s+(\d{4}))?""")
}
