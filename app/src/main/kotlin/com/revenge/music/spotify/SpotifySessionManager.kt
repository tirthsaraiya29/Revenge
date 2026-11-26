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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class SpotifySessionManager @Inject constructor() {

  private companion object {
    const val GRACE_MS = 60_000L
  }

  suspend fun getSession(context: Context): SpotifySession? {
    val preferences = context.dataStore.data.first()
    val spDc = preferences[SpotifySpDcKey]?.takeIf { it.isNotBlank() } ?: return null
    return SpotifySession(
      spDc = spDc,
      spKey = preferences[SpotifySpKeyKey],
      accessToken = preferences[SpotifyAccessTokenKey],
      expiresAt = preferences[SpotifyAccessTokenExpiresAtKey] ?: 0L,
      accountName = preferences[SpotifyAccountNameKey],
      accountAvatarUrl = preferences[SpotifyAccountAvatarUrlKey],
    )
  }

  suspend fun saveSession(context: Context, session: SpotifySession) {
    context.dataStore.edit { preferences ->
      preferences[SpotifySpDcKey] = session.spDc
      session.spKey?.let { preferences[SpotifySpKeyKey] = it }
        ?: preferences.remove(SpotifySpKeyKey)
      session.accessToken?.let { preferences[SpotifyAccessTokenKey] = it }
        ?: preferences.remove(SpotifyAccessTokenKey)
      preferences[SpotifyAccessTokenExpiresAtKey] = session.expiresAt
      session.accountName?.let { preferences[SpotifyAccountNameKey] = it }
        ?: preferences.remove(SpotifyAccountNameKey)
      session.accountAvatarUrl?.let { preferences[SpotifyAccountAvatarUrlKey] = it }
        ?: preferences.remove(SpotifyAccountAvatarUrlKey)
    }
  }

  suspend fun clearSession(context: Context) {
    context.dataStore.edit { preferences ->
      listOf(
          SpotifySpDcKey,
          SpotifySpKeyKey,
          SpotifyAccessTokenKey,
          SpotifyAccessTokenExpiresAtKey,
          SpotifyAccountNameKey,
          SpotifyAccountAvatarUrlKey,
        )
        .forEach { preferences.remove(it) }
    }
    Spotify.accessToken = null
    runCatching { clearWebAuthSession(context) }
  }

  suspend fun getValidAccessToken(context: Context): String? {
    val session = getSession(context) ?: return null
    val cached = session.accessToken
    if (!cached.isNullOrBlank() && session.expiresAt > System.currentTimeMillis() + GRACE_MS) {
      Spotify.accessToken = cached
      return cached
    }
    val result =
      SpotifyAuth.fetchAccessToken(
        spDc = session.spDc,
        spKey = session.spKey.orEmpty(),
      )
    return result.fold(
      onSuccess = { token ->
        Spotify.accessToken = token.accessToken
        saveSession(
          context,
          session.copy(
            accessToken = token.accessToken,
            expiresAt = token.accessTokenExpirationTimestampMs,
          ),
        )
        token.accessToken
      },
      onFailure = { failure ->
        if (failure is Spotify.SpotifyException && failure.statusCode == 401) {
          clearSession(context)
        }
        null
      },
    )
  }
}
