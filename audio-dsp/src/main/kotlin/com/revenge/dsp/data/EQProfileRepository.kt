package com.revenge.dsp.data

import android.content.Context
import android.content.SharedPreferences
import com.revenge.dsp.core.models.ParametricEQ
import com.revenge.dsp.core.models.SavedEQProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Persists equaliser profiles as one JSON document in SharedPreferences. The prefs name and keys
 * are kept stable so profiles saved by earlier builds are still found on upgrade.
 */
@Singleton
class EQProfileRepository
@Inject
constructor(@param:ApplicationContext private val context: Context) {

  private val prefs: SharedPreferences =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
  }

  private val _profiles = MutableStateFlow<List<SavedEQProfile>>(emptyList())
  val profiles: StateFlow<List<SavedEQProfile>> = _profiles.asStateFlow()

  private val _activeProfile = MutableStateFlow<SavedEQProfile?>(null)
  val activeProfile: StateFlow<SavedEQProfile?> = _activeProfile.asStateFlow()

  init {
    restore()
  }

  private fun restore() {
    try {
      val stored = prefs.getString(KEY_PROFILES, null) ?: return
      val loaded = json.decodeFromString<List<SavedEQProfile>>(stored)
      _profiles.value = loaded
      val activeId = prefs.getString(KEY_ACTIVE_PROFILE_ID, null)
      _activeProfile.value = loaded.firstOrNull { it.id == activeId }
    } catch (e: Exception) {
      Timber.tag(TAG).e(e, "Could not read stored EQ profiles; starting empty")
      _profiles.value = emptyList()
      _activeProfile.value = null
    }
  }

  suspend fun saveProfile(profile: SavedEQProfile) =
    withContext(Dispatchers.IO) {
      val updated = _profiles.value.toMutableList()
      val existing = updated.indexOfFirst { it.id == profile.id }
      if (existing >= 0) updated[existing] = profile else updated.add(profile)

      persist(updated)
      _profiles.value = updated
    }

  suspend fun deleteProfile(profileId: String) =
    withContext(Dispatchers.IO) {
      val updated = _profiles.value.filterNot { it.id == profileId }
      persist(updated)
      _profiles.value = updated

      if (_activeProfile.value?.id == profileId) {
        _activeProfile.value = null
        prefs.edit().remove(KEY_ACTIVE_PROFILE_ID).apply()
      }
    }

  suspend fun setActiveProfile(profileId: String?) =
    withContext(Dispatchers.IO) {
      if (profileId == null) {
        _activeProfile.value = null
        prefs.edit().remove(KEY_ACTIVE_PROFILE_ID).apply()
        return@withContext
      }

      val profile = _profiles.value.firstOrNull { it.id == profileId }
      _activeProfile.value = profile
      val editor = prefs.edit()
      if (profile != null) editor.putString(KEY_ACTIVE_PROFILE_ID, profileId)
      else editor.remove(KEY_ACTIVE_PROFILE_ID)
      editor.apply()
    }

  fun getAllProfiles(): List<SavedEQProfile> = _profiles.value

  fun getActiveProfile(): SavedEQProfile? = _activeProfile.value

  /** Creates and stores a custom profile from an imported parametric EQ definition. */
  suspend fun importCustomProfile(name: String, parametricEQ: ParametricEQ) =
    withContext(Dispatchers.IO) {
      saveProfile(
        SavedEQProfile(
          id = "custom_${System.currentTimeMillis()}_${name.hashCode()}",
          name = name,
          deviceModel = name,
          bands = parametricEQ.bands,
          preamp = parametricEQ.preamp,
          isCustom = true,
        ),
      )
    }

  /** Custom profiles only, newest first. */
  fun getSortedProfiles(): List<SavedEQProfile> =
    _profiles.value.filter { it.isCustom }.sortedByDescending { it.addedTimestamp }

  private fun persist(profiles: List<SavedEQProfile>) {
    prefs.edit().putString(KEY_PROFILES, json.encodeToString(profiles)).apply()
  }

  private companion object {
    const val PREFS_NAME = "nanosonic_eq_profiles"
    const val KEY_PROFILES = "eq_profiles"
    const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
    const val TAG = "EQProfileRepository"
  }
}
