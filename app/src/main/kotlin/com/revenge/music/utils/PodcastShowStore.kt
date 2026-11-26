package com.revenge.music.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tracks which podcast-show browse ids the user has followed at least once. */
class PodcastShowStore private constructor(
    private val preferences: SharedPreferences,
) {
    private val _ids = MutableStateFlow(
        preferences.getStringSet(KEY, emptySet())?.toSet() ?: emptySet(),
    )
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    fun add(id: String) {
        if (id.isBlank() || id in _ids.value) return
        val updated = _ids.value + id
        _ids.value = updated
        preferences.edit().putStringSet(KEY, updated).apply()
    }

    companion object {
        private const val KEY = "show_ids"

        @Volatile private var instance: PodcastShowStore? = null

        fun getInstance(context: Context): PodcastShowStore =
            instance ?: synchronized(this) {
                instance ?: PodcastShowStore(
                    context.getSharedPreferences("podcast_shows", Context.MODE_PRIVATE),
                ).also { instance = it }
            }
    }
}
