package com.revenge.music.utils.cipher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * Persistent cache of per-player cipher configuration, seeded from the bundled
 * `player_configs.json` asset and extended at runtime by [PlayerConfigParser].
 *
 * Schema (version 1):
 * ```
 * { "schemaVersion": 1,
 *   "players": { "<playerId>": { "sig": "Fn(48,5831,INPUT)",
 *                                "nClass": "W_",
 *                                "sts": 20602,
 *                                "aliases": ["a6fc27c5"] } } }
 * ```
 * `aliases` are alternative player ids that share the same build. [find] therefore matches both
 * the key and any alias. Runtime entries are kept separately so clearing them restores the
 * bundled snapshot untouched.
 */
object PlayerConfigStore {
  private const val TAG = "PlayerConfigStore"
  private const val BUNDLED_ASSET = "player_configs.json"
  private const val PREFS = "cipher_player_config"
  private const val KEY_OVERRIDES = "overrides"
  private const val SCHEMA_VERSION = 1
  private const val FIELD_PLAYERS = "players"
  private const val FIELD_SCHEMA = "schemaVersion"

  private val lock = Any()

  /** One player build's cipher recipe. All fields are best-effort and may be absent. */
  data class Entry(
    val sig: String? = null,
    val nClass: String? = null,
    val sts: Int? = null,
    val aliases: List<String> = emptyList(),
  )

  @Volatile private var bundled: Map<String, Entry>? = null
  @Volatile private var overrides: MutableMap<String, Entry>? = null
  @Volatile private var loaded = false

  fun initialize(context: Context) {
    synchronized(lock) {
      if (loaded) return
      bundled = readBundled(context)
      overrides = readOverrides(context).toMutableMap()
      loaded = true
      Timber.tag(TAG)
        .d("Loaded %d bundled and %d runtime player configs", bundled?.size ?: 0, overrides?.size ?: 0)
    }
  }

  /** Finds by exact player id, then by any entry listing [playerId] as an alias. */
  fun find(playerId: String): Entry? {
    val state = ensureLoaded() ?: return null
    state.second[playerId]?.let { return it }
    state.first[playerId]?.let { return it }
    return (state.first.asSequence() + state.second.asSequence())
      .map { it.value }
      .firstOrNull { entry -> entry.aliases.any { it == playerId } }
  }

  /** Stores a runtime entry and records the player's date. Replaces any prior entry for the id. */
  fun save(playerId: String, entry: Entry) {
    val context = contextOrNull() ?: return
    synchronized(lock) {
      ensureLoadedLocked(context)
      val map = overrides ?: mutableMapOf<String, Entry>().also { overrides = it }
      map[playerId] = entry
      writeOverridesLocked(context, map)
    }
    PlayerDatesStore.put(playerId)
  }

  /** All known entries, runtime entries overriding bundled ones by key. */
  fun entries(): Map<String, Entry> {
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

  /** Parses the documented JSON document. Malformed individual entries are skipped. */
  fun parse(text: String): Map<String, Entry> {
    val root = runCatching { JSONObject(text) }.getOrNull() ?: return emptyMap()
    val players = root.optJSONObject(FIELD_PLAYERS) ?: return emptyMap()
    val result = LinkedHashMap<String, Entry>(players.length())
    for (playerId in players.keys()) {
      val objectValue = players.optJSONObject(playerId) ?: continue
      result[playerId] = entryFromJson(objectValue)
    }
    return result
  }

  /** Serializes entries in the bundled asset's schema. */
  fun toJson(entries: Map<String, Entry>): String {
    val players = JSONObject()
    for ((playerId, entry) in entries) {
      players.put(playerId, entryToJson(entry))
    }
    return JSONObject()
      .put(FIELD_SCHEMA, SCHEMA_VERSION)
      .put(FIELD_PLAYERS, players)
      .toString()
  }

  private fun entryFromJson(value: JSONObject): Entry {
    val aliases = mutableListOf<String>()
    value.optJSONArray("aliases")?.let { array ->
      for (index in 0 until array.length()) {
        array.optString(index, "").takeIf { it.isNotBlank() }?.let { aliases.add(it) }
      }
    }
    return Entry(
      sig = value.optString("sig", "").takeIf { it.isNotBlank() },
      nClass = value.optString("nClass", "").takeIf { it.isNotBlank() },
      sts = value.optInt("sts", 0).takeIf { it != 0 },
      aliases = aliases,
    )
  }

  private fun entryToJson(entry: Entry): JSONObject {
    val value = JSONObject()
    entry.sig?.let { value.put("sig", it) }
    entry.nClass?.let { value.put("nClass", it) }
    entry.sts?.let { value.put("sts", it) }
    if (entry.aliases.isNotEmpty()) {
      value.put("aliases", JSONArray(entry.aliases))
    }
    return value
  }

  private fun ensureLoaded(): Pair<Map<String, Entry>, Map<String, Entry>>? {
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

  private fun readBundled(context: Context): Map<String, Entry> =
    try {
      val text = context.assets.open(BUNDLED_ASSET).bufferedReader().use { it.readText() }
      parse(text)
    } catch (error: Exception) {
      Timber.tag(TAG).w(error, "Unable to read bundled player configs")
      emptyMap()
    }

  private fun readOverrides(context: Context): Map<String, Entry> =
    try {
      val text =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_OVERRIDES, null)
      if (text.isNullOrBlank()) emptyMap() else parse(text)
    } catch (error: Exception) {
      Timber.tag(TAG).w(error, "Unable to read stored player configs")
      emptyMap()
    }

  private fun writeOverridesLocked(context: Context, map: Map<String, Entry>) {
    try {
      context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putString(KEY_OVERRIDES, toJson(map))
        .apply()
    } catch (error: Exception) {
      Timber.tag(TAG).w(error, "Unable to persist player configs")
    }
  }

  private fun contextOrNull(): Context? =
    if (CipherDeobfuscator.isInitialized) CipherDeobfuscator.appContext else null
}
