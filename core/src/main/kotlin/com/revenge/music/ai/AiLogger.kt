package com.revenge.music.ai

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.revenge.music.utils.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/** Records recent AI actions so the settings screen can show what the assistant did. */
object AiLogger {
  private const val MAX_LOGS = 50
  private val LogsKey = stringPreferencesKey("ai_action_logs")

  suspend fun log(context: Context, action: String, details: String) {
    try {
      val raw = context.dataStore.data.map { it[LogsKey] ?: "[]" }.first()
      val logs = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())

      logs.put(
        JSONObject().apply {
          put("timestamp", System.currentTimeMillis())
          put("action", action)
          put("details", details)
        },
      )

      val trimmed =
        if (logs.length() > MAX_LOGS) {
          JSONArray().also { result ->
            for (i in logs.length() - MAX_LOGS until logs.length()) result.put(logs.get(i))
          }
        } else {
          logs
        }

      context.dataStore.edit { it[LogsKey] = trimmed.toString() }
    } catch (e: Exception) {
      Timber.e(e, "Failed to append AI log")
    }
  }
}
