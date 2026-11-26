package com.revenge.core.ai

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.revenge.core.utils.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber

private const val LOGS_KEY_NAME = "ai_action_logs"
private const val MAX_LOG_ENTRIES = 50

private val logsKey = stringPreferencesKey(LOGS_KEY_NAME)

private fun decodeLogs(raw: String?): List<JsonElement> {
    if (raw == null) return emptyList()
    return try {
        Json.parseToJsonElement(raw) as? JsonArray ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
}

object AiLogger {

    suspend fun log(context: Context, action: String, details: String) {
        try {
            val store = context.dataStore
            val existing = decodeLogs(store.data.first()[logsKey])
            val record = buildJsonObject {
                put("timestamp", System.currentTimeMillis())
                put("action", action)
                put("details", details)
            }
            val appended = existing + record
            val bounded = if (appended.size > MAX_LOG_ENTRIES) {
                appended.subList(appended.size - MAX_LOG_ENTRIES, appended.size)
            } else {
                appended
            }
            val encoded = JsonArray(bounded).toString()
            store.edit { preferences -> preferences[logsKey] = encoded }
        } catch (error: Exception) {
            Timber.e(error)
        }
    }
}
