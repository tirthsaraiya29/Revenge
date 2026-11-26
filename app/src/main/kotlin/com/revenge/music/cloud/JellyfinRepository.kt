package com.revenge.music.cloud

import android.content.Context
import androidx.core.content.edit
import com.revenge.core.db.MusicDatabase
import com.revenge.core.db.entities.ArtistEntity
import com.revenge.core.db.entities.PlaylistEntity
import com.revenge.core.db.entities.PlaylistSongMap
import com.revenge.core.db.entities.SongArtistMap
import com.revenge.core.db.entities.SongEntity
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
class JellyfinRepository
@Inject
constructor(
  @ApplicationContext context: Context,
  private val database: MusicDatabase,
  private val api: JellyfinApiService,
) {
  private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  private val _isLoggedInFlow = MutableStateFlow(readConnected())
  val isLoggedInFlow: StateFlow<Boolean> = _isLoggedInFlow.asStateFlow()

  private val _serverUrlFlow = MutableStateFlow(prefs.getString(KEY_SERVER_URL, null))
  val serverUrlFlow: StateFlow<String?> = _serverUrlFlow.asStateFlow()

  private val _usernameFlow = MutableStateFlow(prefs.getString(KEY_USERNAME, null))
  val usernameFlow: StateFlow<String?> = _usernameFlow.asStateFlow()

  fun getCredentials(): JellyfinCredentials? {
    val url = prefs.getString(KEY_SERVER_URL, null)?.takeIf { it.isNotBlank() } ?: return null
    val user = prefs.getString(KEY_USERNAME, null)?.takeIf { it.isNotBlank() } ?: return null
    return JellyfinCredentials(
      serverUrl = url,
      username = user,
      accessToken = prefs.getString(KEY_ACCESS_TOKEN, null),
      userId = prefs.getString(KEY_USER_ID, null),
      serverId = prefs.getString(KEY_SERVER_ID, null),
    )
  }

  suspend fun login(serverUrl: String, username: String, password: String): Result<Unit> =
    withContext(Dispatchers.IO) {
      exchange(serverUrl, username, password).fold(
        onSuccess = { credentials ->
          prefs.edit {
            putString(KEY_SERVER_URL, credentials.serverUrl)
            putString(KEY_USERNAME, credentials.username)
            putString(KEY_ACCESS_TOKEN, credentials.accessToken)
            putString(KEY_USER_ID, credentials.userId)
            putString(KEY_SERVER_ID, credentials.serverId)
            putBoolean(KEY_CONNECTED, true)
          }
          publish(credentials.serverUrl, credentials.username, connected = true)
          Result.success(Unit)
        },
        onFailure = { Result.failure(it) },
      )
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
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(it) },
      )
    }

  suspend fun getPlaylists(): Result<List<JellyfinPlaylist>> = guarded { api.getPlaylists(it) }

  suspend fun getPlaylistSongs(playlistId: String): Result<List<JellyfinSong>> = guarded {
    api.getPlaylistSongs(it, playlistId)
  }

  suspend fun getSongs(limit: Int = 100): Result<List<JellyfinSong>> = guarded {
    api.getSongs(it, limit)
  }

  suspend fun getRandomSongs(size: Int = 100): Result<List<JellyfinSong>> =
    getSongs(size)

  fun getStreamUrl(songId: String): String? = getCredentials()?.let { api.getStreamUrl(it, songId) }

  fun getItemImageUrl(itemId: String): String? =
    getCredentials()?.let { api.getItemImageUrl(it, itemId) }

  suspend fun syncPlaylistsAndSongs(): Result<Int> =
    withContext(Dispatchers.IO) {
      try {
        val credentials = getCredentials()
          ?: return@withContext Result.failure(Exception("Not logged in"))
        val playlists = api.getPlaylists(credentials).getOrDefault(emptyList())
        var mirrored = 0
        if (playlists.isEmpty()) {
          val songs = api.getSongs(credentials, 100).getOrDefault(emptyList())
          mirrored += mirrorPlaylist(libraryPlaylist(now()), songs)
        } else {
          for (playlist in playlists) {
            val songs = api.getPlaylistSongs(credentials, playlist.id).getOrDefault(emptyList())
            mirrored += mirrorPlaylist(remotePlaylist(playlist), songs)
          }
        }
        Result.success(mirrored)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        Timber.tag(TAG).e(e, "Jellyfin sync failed")
        Result.failure(e)
      }
    }

  private suspend fun exchange(
    serverUrl: String,
    username: String,
    password: String,
  ): Result<JellyfinCredentials> =
    api.authenticate(serverUrl, username, password)

  private suspend fun <T> guarded(
    call: suspend (JellyfinCredentials) -> Result<T>,
  ): Result<T> =
    withContext(Dispatchers.IO) {
      val credentials = getCredentials()
        ?: return@withContext Result.failure(Exception("Not logged in"))
      call(credentials)
    }

  private suspend fun mirrorPlaylist(playlist: PlaylistEntity, songs: List<JellyfinSong>): Int {
    database.withTransaction {
      insert(playlist)
      update(playlist)
      songs.forEachIndexed { index, song -> persistSong(song, playlist.id, index) }
    }
    return songs.size
  }

  private fun persistSong(song: JellyfinSong, playlistId: String, position: Int) {
    val timestamp = now()
    val entity =
      SongEntity(
        id = "jellyfin_${song.id}",
        title = song.title,
        duration = (song.durationMs / 1000).toInt(),
        thumbnailUrl = if (song.hasCover) getItemImageUrl(song.id) else null,
        albumName = song.album,
        inLibrary = timestamp,
        year = song.year,
      )
    database.insert(entity)
    database.update(entity)
    val artistId = "jellyfin_artist_${song.artist.hashCode()}"
    database.insert(ArtistEntity(id = artistId, name = song.artist, lastUpdateTime = timestamp))
    database.insert(SongArtistMap(songId = entity.id, artistId = artistId, position = 0))
    database.insert(PlaylistSongMap(playlistId = playlistId, songId = entity.id, position = position))
  }

  private fun remotePlaylist(playlist: JellyfinPlaylist) =
    PlaylistEntity(
      id = "jellyfin_pl_${playlist.id}",
      name = "[Jellyfin] ${playlist.name}",
      isEditable = false,
      bookmarkedAt = now(),
      createdAt = now(),
      lastUpdateTime = now(),
      thumbnailUrl = if (playlist.hasCover) getItemImageUrl(playlist.id) else null,
    )

  private fun libraryPlaylist(timestamp: LocalDateTime) =
    PlaylistEntity(
      id = "jellyfin_pl_library",
      name = "[Jellyfin] Library",
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
    const val TAG = "JellyfinRepository"
    const val PREFS = "jellyfin_prefs"
    const val KEY_SERVER_URL = "server_url"
    const val KEY_USERNAME = "username"
    const val KEY_ACCESS_TOKEN = "access_token"
    const val KEY_USER_ID = "user_id"
    const val KEY_SERVER_ID = "server_id"
    const val KEY_CONNECTED = "connected"
  }
}
