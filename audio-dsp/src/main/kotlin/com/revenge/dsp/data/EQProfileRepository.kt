package com.revenge.dsp.data

import android.content.Context
import com.revenge.dsp.core.models.ParametricEQ
import com.revenge.dsp.core.models.SavedEQProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

@Singleton
class EQProfileRepository
@Inject
constructor(
  @param:ApplicationContext context: Context,
) {
  private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
  private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
  }
  private val writeLock = Mutex()

  private val profilesState = MutableStateFlow<List<SavedEQProfile>>(emptyList())
  val profiles: StateFlow<List<SavedEQProfile>> = profilesState.asStateFlow()

  private val activeProfileState = MutableStateFlow<SavedEQProfile?>(null)
  val activeProfile: StateFlow<SavedEQProfile?> = activeProfileState.asStateFlow()

  init {
    profilesState.value = readProfilesFromDisk()
    val activeId = preferences.getString(KEY_ACTIVE_PROFILE_ID, null)
    activeProfileState.value = profilesState.value.firstOrNull { it.id == activeId }
  }

  suspend fun saveProfile(profile: SavedEQProfile) =
    withContext(Dispatchers.IO) {
      writeLock.withLock {
        val current = profilesState.value
        val existing = current.indexOfFirst { it.id == profile.id }
        val updated =
          if (existing >= 0) {
            current.toMutableList().also { it[existing] = profile }
          } else {
            current + profile
          }
        persistProfiles(updated)
        profilesState.value = updated
      }
    }

  suspend fun deleteProfile(profileId: String) =
    withContext(Dispatchers.IO) {
      writeLock.withLock {
        val updated = profilesState.value.filterNot { it.id == profileId }
        val editor = preferences.edit().putString(KEY_PROFILES, json.encodeToString(updated))
        if (activeProfileState.value?.id == profileId) {
          editor.remove(KEY_ACTIVE_PROFILE_ID)
        }
        editor.commit()
        profilesState.value = updated
        if (activeProfileState.value?.id == profileId) {
          activeProfileState.value = null
        }
      }
    }

  suspend fun setActiveProfile(profileId: String?) =
    withContext(Dispatchers.IO) {
      writeLock.withLock {
        if (profileId == null) {
          preferences.edit().remove(KEY_ACTIVE_PROFILE_ID).commit()
          activeProfileState.value = null
        } else {
          val found = profilesState.value.firstOrNull { it.id == profileId }
          val editor = preferences.edit()
          if (found != null) {
            editor.putString(KEY_ACTIVE_PROFILE_ID, profileId)
          } else {
            editor.remove(KEY_ACTIVE_PROFILE_ID)
          }
          editor.commit()
          activeProfileState.value = found
        }
      }
    }

  fun getAllProfiles(): List<SavedEQProfile> = profilesState.value

  fun getActiveProfile(): SavedEQProfile? = activeProfileState.value

  suspend fun importCustomProfile(name: String, parametricEQ: ParametricEQ) {
    val imported =
      SavedEQProfile(
        id = "custom_${System.currentTimeMillis()}_${name.hashCode()}",
        name = name,
        deviceModel = name,
        bands = parametricEQ.bands,
        preamp = parametricEQ.preamp,
        isCustom = true,
      )
    saveProfile(imported)
  }

  fun getSortedProfiles(): List<SavedEQProfile> =
    profilesState.value.filter { it.isCustom }.sortedByDescending { it.addedTimestamp }

  private fun readProfilesFromDisk(): List<SavedEQProfile> {
    val stored = preferences.getString(KEY_PROFILES, null) ?: return emptyList()
    return try {
      json.decodeFromString<List<SavedEQProfile>>(stored)
    } catch (error: Exception) {
      Timber.e(error, "Unable to read stored EQ profiles")
      emptyList()
    }
  }

  private fun persistProfiles(profiles: List<SavedEQProfile>) {
    preferences.edit().putString(KEY_PROFILES, json.encodeToString(profiles)).commit()
  }

  private companion object {
    const val PREFERENCES_NAME = "nanosonic_eq_profiles"
    const val KEY_PROFILES = "eq_profiles"
    const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
  }
}
