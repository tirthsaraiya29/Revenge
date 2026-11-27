package com.revenge.core.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class PlaybackLogLevel {
  INFO,
  WARNING,
  ERROR,
  DEBUG,
  BOT,
}

data class PlaybackLogEntry(
  val timestamp: String,
  val level: PlaybackLogLevel,
  val message: String,
  val details: String? = null,
)

object PlaybackLogManager {

  private const val MAX_ENTRIES = 500

  private val clock = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
  private val state = MutableStateFlow<List<PlaybackLogEntry>>(emptyList())

  val logs: StateFlow<List<PlaybackLogEntry>> = state.asStateFlow()

  fun log(level: PlaybackLogLevel, message: String, details: String? = null) {
    val stamp = synchronized(clock) { clock.format(Date()) }
    val entry =
      PlaybackLogEntry(timestamp = stamp, level = level, message = message, details = details)
    state.update { current -> (current + entry).takeLast(MAX_ENTRIES) }
  }

  fun clearLogs() {
    state.value = emptyList()
  }
}
