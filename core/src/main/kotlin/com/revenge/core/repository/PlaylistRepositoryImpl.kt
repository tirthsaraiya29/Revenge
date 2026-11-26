package com.revenge.core.repository

import com.revenge.core.mapping.toDomain
import com.revenge.domain.models.Playlist
import com.revenge.domain.models.Song
import com.revenge.domain.repositories.PlaylistRepository
import com.revenge.music.db.MusicDatabase
import com.revenge.music.db.entities.PlaylistSongMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class PlaylistRepositoryImpl
@Inject
constructor(
  private val database: MusicDatabase,
) : PlaylistRepository {

  override fun getAllPlaylists(): Flow<List<Playlist>> =
    database.playlistsByNameAsc().map { rows -> rows.map { it.toDomain() } }

  override fun getPlaylistSongs(playlistId: String): Flow<List<Song>> =
    database.playlistSongs(playlistId).map { rows -> rows.map { it.song.toDomain() } }

  override suspend fun addSongToPlaylist(playlistId: String, songId: String) {
    database.query {
      val existing = playlistSongMaps(playlistId, 0)
      val nextPosition = (existing.maxOfOrNull { it.position } ?: -1) + 1
      insert(PlaylistSongMap(playlistId = playlistId, songId = songId, position = nextPosition))
    }
  }

  override suspend fun removeSongFromPlaylist(playlistId: String, songId: String) {
    database.query {
      playlistSongMaps(songId).filter { it.playlistId == playlistId }.forEach { delete(it) }
    }
  }
}
