package com.revenge.core.repository

import com.revenge.core.mapping.toDomain
import com.revenge.domain.models.Song as DomainSong
import com.revenge.domain.repositories.SongRepository
import com.revenge.music.db.MusicDatabase
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class SongRepositoryImpl
@Inject
constructor(
  private val database: MusicDatabase,
) : SongRepository {

  override fun getSong(id: String): Flow<DomainSong?> = database.song(id).map { it?.toDomain() }

  override fun getLikedSongs(): Flow<List<DomainSong>> =
    database.likedSongsByRowIdAsc().map { songs -> songs.map { it.toDomain() } }

  override fun getRecentlyPlayed(limit: Int): Flow<List<DomainSong>> =
    database.events().map { events ->
      events.map { it.song.toDomain() }.distinctBy { it.id }.take(limit)
    }

  override suspend fun updateLiked(id: String, isLiked: Boolean) {
    val likedDate = if (isLiked) LocalDateTime.now() else null
    database.query { updateLikedStatus(id, isLiked, likedDate) }
  }

  override suspend fun incrementPlayTime(id: String, durationMs: Long) {
    database.query { incrementTotalPlayTime(id, durationMs) }
  }
}
