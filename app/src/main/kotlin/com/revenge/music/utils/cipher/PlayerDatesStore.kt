package com.revenge.music.utils.cipher

import android.content.Context
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import org.json.JSONObject
import timber.log.Timber

/**
 * Publication date of each known YouTube player build, keyed by player id.
 *
 * Dates are seeded from the bundled `player_dates.json` asset and extended at runtime with the
 * entries discovered by [PlayerJsFetcher]. Each value is the UTC calendar day `yyyy-MM-dd`; it is
 * used to prefer the newest cached player and to age out stale entries.
 */
object PlayerDatesStore {
  private const val TAG = "PlayerDatesStore"
  private const val BUNDLED_ASSET = "player_dates.json"
  private const val PREFS = "cipher_player_dates"
  private const val KEY_OVERRIDES = "overrides"

  private val dateFormat: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
  private val lock = Any()

  @Volatile private var bundled: Map<String, String>? = null
  @Volatile private var overrides: MutableMap<String, String>? = null
  @Volatile private var loaded = false

  fun initialize(context: Context) {
    synchronized(lock) {
      if (loaded) return
      bundled = readBundled(context)
      overrides = readOverrides(context).toMutableMap()
      loaded = true
      Timber.tag(TAG)
        .d("Loaded %d bundled and %d runtime player dates", bundled?.size ?: 0, overrides?.size ?: 0)
    }
  }

  /** Date for [playerId], preferring a runtime entry over the bundled snapshot. */
  fun dateFor(playerId: String): String? {
    val state = ensureLoaded() ?: return null
    return state.second[playerId] ?: state.first[playerId]
  }

  /** Records that [playerId] was seen today (or on [date]); persists the runtime override. */
  fun put(playerId: String, date: String = today()) {
    val context = contextOrNull() ?: return
    val normalized = parseDate(date)?.format(dateFormat) ?: return
    synchronized(lock) {
      ensureLoadedLocked(context)
      val map = overrides ?: mutableMapOf<String, String>().also { overrides = it }
      if (map[playerId] == normalized) return
      map[playerId] = normalized
      writeOverridesLocked(context, map)
    }
  }

  /** True when [playerId] has no known date or its date is within [ttlDays] of today. */
  fun isFresh(playerId: String, ttlDays: Int): Boolean {
    val date = parseDate(dateFor(playerId)) ?: return true
    val ageDays = ChronoUnit.DAYS.between(date, LocalDate.now(ZoneOffset.UTC))
    return ageDays <= ttlDays
  }

  fun all(): Map<String, String> {
    val state = ensureLoaded() ?: return emptyMap()
    return state.first + state.second
  }

  fun clearRuntime() {
    val context = contextOrNull() ?: return
    synchronized(lock) {
      overrides = mutableMapOf()
      context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_OVERRIDES).apply()
    }
  }

  fun today(): String = LocalDate.now(ZoneOffset.UTC).format(dateFormat)

  private fun ensureLoaded(): Pair<Map<String, String>, Map<String, String>>? {
    val context = contextOrNull() ?: return null
    synchronized(lock) {
      ensureLoadedLocked(context)
      return (bundled ?: emptyMap()) to (overrides ?: emptyMap())
    }
  }

  private fun ensureLoadedLocked(context: Context) {
    if (loaded) return
    bundled = readBundled(context)
    overrides = readOverrides(context).toMutableMap()
    loaded = true
  }

  private fun readBundled(context: Context): Map<String, String> =
    try {
      val text = context.assets.open(BUNDLED_ASSET).bufferedReader().use { it.readText() }
      parse(text)
    } catch (error: Exception) {
      Timber.tag(TAG).w(error, "Unable to read bundled player dates")
      emptyMap()
    }

  private fun readOverrides(context: Context): Map<String, String> =
    try {
      val text =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_OVERRIDES, null)
      if (text.isNullOrBlank()) emptyMap() else parse(text)
    } catch (error: Exception) {
      Timber.tag(TAG).w(error, "Unable to read stored player dates")
      emptyMap()
    }

  private fun writeOverridesLocked(context: Context, map: Map<String, String>) {
    try {
      val json = JSONObject(map).toString()
      context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putString(KEY_OVERRIDES, json)
        .apply()
    } catch (error: Exception) {
      Timber.tag(TAG).w(error, "Unable to persist player dates")
    }
  }

  private fun parse(text: String): Map<String, String> {
    val root = JSONObject(text)
    val result = LinkedHashMap<String, String>(root.length())
    for (key in root.keys()) {
      val date = parseDate(root.optString(key, "")) ?: continue
      result[key] = date.format(dateFormat)
    }
    return result
  }

  private fun parseDate(value: String?): LocalDate? {
    if (value.isNullOrBlank()) return null
    return try {
      LocalDate.parse(value, dateFormat)
    } catch (error: DateTimeParseException) {
      null
    }
  }

  private fun contextOrNull(): Context? =
    if (CipherDeobfuscator.isInitialized) CipherDeobfuscator.appContext else null
}
