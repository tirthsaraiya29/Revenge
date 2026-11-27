package com.revenge.music.generate

import com.revenge.core.db.daos.SongPlayStatsDao
import com.revenge.core.db.entities.SongPlayStatsEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

@Singleton
class SongPlayStatsRepository
@Inject
constructor(
  private val dao: SongPlayStatsDao,
) {
  private val mutex = Mutex()

  suspend fun recordListenedMs(
    title: String,
    artist: String,
    videoId: String?,
    artworkUrl: String?,
    listenedMs: Long,
    completed: Boolean,
  ) {
    if (title.isBlank()) return
    record(title, artist, videoId, artworkUrl, listenedMs, completed, skip = false)
  }

  suspend fun recordSkip(
    title: String,
    artist: String,
    videoId: String?,
    artworkUrl: String?,
    listenedMs: Long,
  ) {
    if (title.isBlank()) return
    record(title, artist, videoId, artworkUrl, listenedMs, completed = false, skip = true)
  }

  private suspend fun record(
    title: String,
    artist: String,
    videoId: String?,
    artworkUrl: String?,
    listenedMs: Long,
    completed: Boolean,
    skip: Boolean,
  ) {
    withContext(Dispatchers.IO) {
      mutex.withLock {
        try {
          val key = trackKeyOf(title, artist)
          val now = System.currentTimeMillis()
          val existing = dao.find(key)
          val updated =
            if (existing == null) {
              SongPlayStatsEntity(
                trackKey = key,
                title = title,
                artist = artist,
                videoId = videoId,
                artworkUrl = artworkUrl,
                totalPlayTimeMs = listenedMs.coerceAtLeast(0),
                playCount = if (completed && !skip) 1 else 0,
                skipCount = if (skip) 1 else 0,
                lastPlayedAtMillis = now,
              )
            } else {
              existing.copy(
                totalPlayTimeMs = existing.totalPlayTimeMs + listenedMs.coerceAtLeast(0),
                playCount = existing.playCount + if (completed && !skip) 1 else 0,
                skipCount = existing.skipCount + if (skip) 1 else 0,
                videoId = videoId ?: existing.videoId,
                artworkUrl = artworkUrl ?: existing.artworkUrl,
                lastPlayedAtMillis = now,
              )
            }
          dao.upsert(updated)
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (t: Throwable) {
          Timber.e(t, "Failed to record play stats")
        }
      }
    }
  }
}
