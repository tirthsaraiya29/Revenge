/*
 * Revenge (2026)
 * © Chartreux Westia — github.com/koiverse
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Adapted from Vivi Music (vivimusic Project (C) 2026, GPL-3.0)
 * https://github.com/vivizzz007/vivi-music
 */

package com.revenge.music.spotify

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.revenge.music.constants.SpotifyAccessTokenExpiresAtKey
import com.revenge.music.constants.SpotifyAccessTokenKey
import com.revenge.music.constants.SpotifyAccountAvatarUrlKey
import com.revenge.music.constants.SpotifyAccountNameKey
import com.revenge.music.constants.SpotifySpDcKey
import com.revenge.music.constants.SpotifySpKeyKey
import com.revenge.music.spotify.models.SpotifySession
import com.revenge.music.utils.clearWebAuthSession
import com.revenge.music.utils.dataStore
import com.revenge.music.utils.reportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Manages Spotify authentication session state, persistence, and token refreshes.
 *
 * Preserves Revenge's individual DataStore preference keys so all downstream settings, library
 * observers, and playback components continue working without disruption.
 */
object SpotifySessionManager {
  private const val TOKEN_EXPIRY_GRACE_MS = 60_000L

  suspend fun getSession(context: Context): SpotifySession? =
    withContext(Dispatchers.IO) {
      val prefs = context.dataStore.data.first()
      val spDc = prefs[SpotifySpDcKey].orEmpty()
      if (spDc.isBlank()) return@withContext null

      SpotifySession(
        spDc = spDc,
        spKey = prefs[SpotifySpKeyKey],
        accessToken = prefs[SpotifyAccessTokenKey],
        expiresAt = prefs[SpotifyAccessTokenExpiresAtKey] ?: 0L,
        accountName = prefs[SpotifyAccountNameKey],
        accountAvatarUrl = prefs[SpotifyAccountAvatarUrlKey],
      )
    }

  suspend fun saveSession(
    context: Context,
    session: SpotifySession,
  ) =
    withContext(Dispatchers.IO) {
      context.dataStore.edit { prefs ->
        prefs[SpotifySpDcKey] = session.spDc
        if (!session.spKey.isNullOrBlank()) {
          prefs[SpotifySpKeyKey] = session.spKey
        } else {
          prefs.remove(SpotifySpKeyKey)
        }
        if (!session.accessToken.isNullOrBlank()) {
          prefs[SpotifyAccessTokenKey] = session.accessToken
          prefs[SpotifyAccessTokenExpiresAtKey] = session.expiresAt
        }
        if (!session.accountName.isNullOrBlank()) {
          prefs[SpotifyAccountNameKey] = session.accountName
        }
        if (!session.accountAvatarUrl.isNullOrBlank()) {
          prefs[SpotifyAccountAvatarUrlKey] = session.accountAvatarUrl
        }
      }
    }

  suspend fun clearSession(context: Context) =
    withContext(Dispatchers.IO) {
      context.dataStore.edit { prefs ->
        prefs.remove(SpotifySpDcKey)
        prefs.remove(SpotifySpKeyKey)
        prefs.remove(SpotifyAccessTokenKey)
        prefs.remove(SpotifyAccessTokenExpiresAtKey)
        prefs.remove(SpotifyAccountNameKey)
        prefs.remove(SpotifyAccountAvatarUrlKey)
      }
      Spotify.accessToken = null
      runCatching { clearWebAuthSession(context) }.onFailure(::reportException)
    }

  /**
   * Retrieves an active, non-expired access token. If the cached token is expired or missing,
   * automatically refreshes it using the persisted `sp_dc` cookie.
   */
  suspend fun getValidAccessToken(context: Context): String? =
    withContext(Dispatchers.IO) {
      val session = getSession(context) ?: return@withContext null

      if (
        !session.accessToken.isNullOrBlank() &&
          session.expiresAt > System.currentTimeMillis() + TOKEN_EXPIRY_GRACE_MS
      ) {
        Spotify.accessToken = session.accessToken
        return@withContext session.accessToken
      }

      try {
        val token = SpotifyAuth.fetchAccessToken(session.spDc, session.spKey.orEmpty()).getOrThrow()
        Spotify.accessToken = token.accessToken

        context.dataStore.edit { prefs ->
          prefs[SpotifyAccessTokenKey] = token.accessToken
          prefs[SpotifyAccessTokenExpiresAtKey] = token.accessTokenExpirationTimestampMs
        }
        return@withContext token.accessToken
      } catch (e: Exception) {
        Timber.e(e, "Failed to refresh Spotify access token")
        if ((e as? Spotify.SpotifyException)?.statusCode == 401) {
          clearSession(context)
        }
        return@withContext null
      }
    }
}
