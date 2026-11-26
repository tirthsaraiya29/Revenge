package com.revenge.core.repository

import com.revenge.core.mapping.toDomain
import com.revenge.domain.models.Song
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

  override fun getSong(id: String): Flow<Song?> = database.song(id).map { it?.toDomain() }

  override fun getLikedSongs(): Flow<List<Song>> =
    database.likedSongsByRowIdAsc().map { rows -> rows.map { it.toDomain() } }

  override fun getRecentlyPlayed(limit: Int): Flow<List<Song>> =
    database.events().map { events ->
      if (limit <= 0) {
        emptyList()
      } else {
        // events() is newest-first, so distinctBy keeps the most recent occurrence of each song.
        events.map { it.song.toDomain() }.distinctBy { it.id }.take(limit)
      }
    }

  override suspend fun updateLiked(id: String, isLiked: Boolean) {
    val likedDate = if (isLiked) LocalDateTime.now() else null
    database.query { updateLikedStatus(id, isLiked, likedDate) }
  }

  override suspend fun incrementPlayTime(id: String, durationMs: Long) {
    database.query { incrementTotalPlayTime(id, durationMs) }
  }
}
