package com.revenge.music.utils

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persisted set of followed podcast shows. Backed by SharedPreferences so every process reader
 * sees the same ids; the in-memory flow mirrors the stored set.
 */
@Singleton
class PodcastShowStore @Inject constructor(@ApplicationContext context: Context) {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val mutableIds = MutableStateFlow(load())
    val ids: StateFlow<Set<String>> = mutableIds.asStateFlow()

    /** Adds [id] once; repeated calls are no-ops. */
    fun add(id: String) {
        if (id.isBlank()) return
        val current = mutableIds.value
        if (id in current) return
        val next = current + id
        mutableIds.value = next
        preferences.edit { putStringSet(KEY_IDS, next) }
    }

    private fun load(): Set<String> =
        preferences.getStringSet(KEY_IDS, emptySet())?.toSet().orEmpty()

    companion object {
        private const val PREFERENCES_NAME = "podcast_shows"
        private const val KEY_IDS = "ids"
    }
}
