package com.revenge.core.repository

import com.revenge.core.mapping.toDomain
import com.revenge.domain.models.Playlist as DomainPlaylist
import com.revenge.domain.models.Song as DomainSong
import com.revenge.domain.repositories.PlaylistRepository
import com.revenge.music.db.MusicDatabase
import com.revenge.music.db.entities.PlaylistSongMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val database: MusicDatabase,
) : PlaylistRepository {

    override fun getAllPlaylists(): Flow<List<DomainPlaylist>> =
        database.playlistsByNameAsc().map { playlists -> playlists.map { it.toDomain() } }

    override fun getPlaylistSongs(playlistId: String): Flow<List<DomainSong>> =
        database.playlistSongs(playlistId).map { entries -> entries.map { it.song.toDomain() } }

    override suspend fun addSongToPlaylist(playlistId: String, songId: String) {
        database.query {
            val lastPosition = playlistSongMaps(playlistId, 0).maxOfOrNull { it.position }
            insert(
                PlaylistSongMap(
                    playlistId = playlistId,
                    songId = songId,
                    position = lastPosition?.let { it + 1 } ?: 0,
                )
            )
        }
    }

    override suspend fun removeSongFromPlaylist(playlistId: String, songId: String) {
        database.query {
            playlistSongMaps(songId)
                .filter { it.playlistId == playlistId }
                .forEach { delete(it) }
        }
    }
}
