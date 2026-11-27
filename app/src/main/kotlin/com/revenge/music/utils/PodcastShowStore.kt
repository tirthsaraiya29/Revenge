package com.revenge.music.utils

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tracks which podcast-show browse ids the user has followed at least once. */
@Singleton
class PodcastShowStore
@Inject
constructor(
  @ApplicationContext context: Context,
) {
  private val preferences: SharedPreferences =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _ids =
    MutableStateFlow(
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
    private const val PREFS_NAME = "podcast_shows"
    private const val KEY = "show_ids"
  }
}
