package com.revenge.music.cloud

import android.content.Context
import androidx.core.content.edit
import com.revenge.music.db.MusicDatabase
import com.revenge.music.db.entities.ArtistEntity
import com.revenge.music.db.entities.PlaylistEntity
import com.revenge.music.db.entities.PlaylistSongMap
import com.revenge.music.db.entities.SongArtistMap
import com.revenge.music.db.entities.SongEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber

@Singleton
class NavidromeRepository
@Inject
constructor(
  @ApplicationContext context: Context,
  private val database: MusicDatabase,
  private val api: NavidromeApiService,
) {
  private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  private val _isLoggedInFlow = MutableStateFlow(readConnected())
  val isLoggedInFlow: StateFlow<Boolean> = _isLoggedInFlow.asStateFlow()

  private val _serverUrlFlow = MutableStateFlow(prefs.getString(KEY_SERVER_URL, null))
  val serverUrlFlow: StateFlow<String?> = _serverUrlFlow.asStateFlow()

  private val _usernameFlow = MutableStateFlow(prefs.getString(KEY_USERNAME, null))
  val usernameFlow: StateFlow<String?> = _usernameFlow.asStateFlow()

  fun getCredentials(): NavidromeCredentials? {
    val url = prefs.getString(KEY_SERVER_URL, null)?.takeIf { it.isNotBlank() } ?: return null
    val user = prefs.getString(KEY_USERNAME, null)?.takeIf { it.isNotBlank() } ?: return null
    val password = prefs.getString(KEY_PASSWORD, null) ?: return null
    val method =
      runCatching { NavidromeAuthMethod.valueOf(prefs.getString(KEY_AUTH_METHOD, "TOKEN")!!) }
        .getOrDefault(NavidromeAuthMethod.TOKEN)
    return NavidromeCredentials(url, user, password, method)
  }

  suspend fun login(serverUrl: String, username: String, password: String): Result<Unit> =
    withContext(Dispatchers.IO) {
      val url = serverUrl.trim().trimEnd('/')
      val user = username.trim()
      val tokenAttempt =
        api.ping(NavidromeCredentials(url, user, password, NavidromeAuthMethod.TOKEN))
      val effective =
        tokenAttempt.getOrNull()
          ?: api.ping(NavidromeCredentials(url, user, password, NavidromeAuthMethod.PASSWORD))
              .getOrNull()
      if (effective == null) {
        return@withContext Result.failure(
          tokenAttempt.exceptionOrNull() ?: Exception("Login failed"),
        )
      }
      prefs.edit {
        putString(KEY_SERVER_URL, url)
        putString(KEY_USERNAME, user)
        putString(KEY_PASSWORD, password)
        putString(KEY_AUTH_METHOD, effective.authMethod.name)
        putBoolean(KEY_CONNECTED, true)
      }
      publish(url, user, connected = true)
      Result.success(Unit)
    }

  fun logout() {
    prefs.edit { clear() }
    publish(null, null, connected = false)
  }

  suspend fun ping(): Result<String> =
    withContext(Dispatchers.IO) {
      val credentials = getCredentials()
        ?: return@withContext Result.failure(Exception("Not logged in"))
      api.ping(credentials).fold(
        onSuccess = { updated ->
          prefs.edit { putString(KEY_AUTH_METHOD, updated.authMethod.name) }
          Result.success(
            "${updated.serverUrl} - ${updated.username} (${updated.authMethod.name})",
          )
        },
        onFailure = { Result.failure(it) },
      )
    }

  suspend fun getPlaylists(): Result<List<NavidromePlaylist>> = guarded {
    api.getPlaylists(it)
  }

  suspend fun getPlaylistSongs(playlistId: String): Result<List<NavidromeSong>> = guarded {
    api.getPlaylistSongs(it, playlistId)
  }

  suspend fun getRandomSongs(size: Int = 50): Result<List<NavidromeSong>> = guarded {
    api.getRandomSongs(it, size)
  }

  fun getStreamUrl(songId: String): String? = getCredentials()?.let { api.getStreamUrl(it, songId) }

  fun getCoverArtUrl(coverArtId: String): String? =
    getCredentials()?.let { api.getCoverArtUrl(it, coverArtId) }

  suspend fun syncPlaylistsAndSongs(): Result<Int> =
    withContext(Dispatchers.IO) {
      try {
        val credentials = getCredentials()
          ?: return@withContext Result.failure(Exception("Not logged in"))
        val playlists = api.getPlaylists(credentials).getOrDefault(emptyList())
        var count = 0
        if (playlists.isEmpty()) {
          val songs = api.getRandomSongs(credentials, 50).getOrDefault(emptyList())
          val playlist = libraryPlaylist(now())
          count += mirrorPlaylist(playlist, songs)
        } else {
          for (playlist in playlists) {
            val songs = api.getPlaylistSongs(credentials, playlist.id).getOrDefault(emptyList())
            count += mirrorPlaylist(remotePlaylist(playlist), songs)
          }
        }
        Result.success(count)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        Timber.tag(TAG).e(e, "Navidrome sync failed")
        Result.failure(e)
      }
    }

  private suspend fun <T> guarded(
    call: suspend (NavidromeCredentials) -> Result<T>,
  ): Result<T> =
    withContext(Dispatchers.IO) {
      val credentials = getCredentials()
        ?: return@withContext Result.failure(Exception("Not logged in"))
      call(credentials)
    }

  private suspend fun mirrorPlaylist(
    playlist: PlaylistEntity,
    songs: List<NavidromeSong>,
  ): Int {
    database.withTransaction {
      insert(playlist)
      update(playlist)
      songs.forEachIndexed { index, song -> persistSong(song, playlist.id, index) }
    }
    return songs.size
  }

  private fun persistSong(song: NavidromeSong, playlistId: String, position: Int) {
    val timestamp = now()
    val entity =
      SongEntity(
        id = "navidrome_${song.id}",
        title = song.title,
        duration = song.duration.toInt(),
        thumbnailUrl = song.coverArt?.let { getCoverArtUrl(it) },
        albumName = song.album,
        inLibrary = timestamp,
        year = song.year,
      )
    insert(entity)
    update(entity)
    val artistId = "navidrome_artist_${song.artist.hashCode()}"
    insert(ArtistEntity(id = artistId, name = song.artist, lastUpdateTime = timestamp))
    insert(SongArtistMap(songId = entity.id, artistId = artistId, position = 0))
    insert(PlaylistSongMap(playlistId = playlistId, songId = entity.id, position = position))
  }

  private fun remotePlaylist(playlist: NavidromePlaylist) =
    PlaylistEntity(
      id = "navidrome_pl_${playlist.id}",
      name = "[Subsonic] ${playlist.name}",
      isEditable = false,
      bookmarkedAt = now(),
      createdAt = now(),
      lastUpdateTime = now(),
      thumbnailUrl = playlist.coverArt?.let { getCoverArtUrl(it) },
    )

  private fun libraryPlaylist(timestamp: LocalDateTime) =
    PlaylistEntity(
      id = "navidrome_pl_library",
      name = "[Subsonic] Library",
      isEditable = false,
      bookmarkedAt = timestamp,
      createdAt = timestamp,
      lastUpdateTime = timestamp,
    )

  private fun readConnected(): Boolean =
    prefs.getBoolean(KEY_CONNECTED, false) &&
      !prefs.getString(KEY_SERVER_URL, null).isNullOrBlank() &&
      !prefs.getString(KEY_USERNAME, null).isNullOrBlank()

  private fun publish(url: String?, username: String?, connected: Boolean) {
    _serverUrlFlow.value = url
    _usernameFlow.value = username
    _isLoggedInFlow.value = connected && !url.isNullOrBlank() && !username.isNullOrBlank()
  }

  private fun now(): LocalDateTime = LocalDateTime.now()

  private companion object {
    const val TAG = "NavidromeRepository"
    const val PREFS = "navidrome_prefs"
    const val KEY_SERVER_URL = "server_url"
    const val KEY_USERNAME = "username"
    const val KEY_PASSWORD = "password"
    const val KEY_AUTH_METHOD = "auth_method"
    const val KEY_CONNECTED = "connected"
  }
}
