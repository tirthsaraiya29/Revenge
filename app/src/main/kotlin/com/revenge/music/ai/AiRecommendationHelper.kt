package com.revenge.music.ai

import android.content.Context
import com.revenge.core.ai.AiLogger
import com.revenge.core.db.MusicDatabase
import com.revenge.core.db.entities.PlaylistEntity
import com.revenge.core.db.entities.PlaylistSongMap
import com.revenge.innertube.YouTube
import com.revenge.innertube.models.SongItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@Singleton
class AiRecommendationHelper
@Inject
constructor(
  private val database: MusicDatabase,
) {
  companion object {
    const val PLAYLIST_NAME = "From your taste by Revenge"
  }

  suspend fun generateRecommendations(
    context: Context,
    onLog: ((String) -> Unit)? = null,
  ): Unit =
    withContext(Dispatchers.IO) {
      val topPlayed = runCatching {
        database.songPlayStatsDao.observeTopPlayed(20).first()
      }
        .getOrDefault(emptyList())
      val liked = runCatching {
        database.likedSongsByCreateDateAsc().first()
      }
        .getOrDefault(emptyList())

      data class Seed(val id: String, val title: String, val artist: String)

      val seeds = buildList {
        topPlayed.forEach {
          add(Seed(it.videoId ?: it.trackKey, it.title, it.artist))
        }
        liked.take(20).forEach { song ->
          add(
            Seed(
              song.song.id,
              song.song.title,
              song.artists.firstOrNull()?.name.orEmpty(),
            ),
          )
        }
      }
        .distinctBy { it.id }
        .take(30)

      if (seeds.isEmpty()) {
        onLog?.invoke("No listening history to build recommendations from.")
        return@withContext
      }

      val taste = seeds.joinToString("\n") { "${it.title} by ${it.artist}" }
      val system = "Return only a raw JSON array of {\"title\":\"...\",\"artist\":\"...\"} songs."
      val user = "Recommend songs similar to this taste:\n$taste"
      val content =
        AiOpenRouter.complete(context, system, user) { message ->
          onLog?.invoke(message)
        } ?: return@withContext

      val requested =
        parseArray(content)
          ?: run {
            onLog?.invoke("AI returned unparsable JSON.")
            return@withContext
          }
      val resolved = ArrayList<SongItem>()
      for (song in requested) {
        if (song.title.isBlank() || song.artist.isBlank()) continue
        val found =
          YouTube.search(
              "${song.title} ${song.artist}",
              YouTube.SearchFilter.FILTER_SONG,
            )
            .getOrNull()
            ?.items
            ?.filterIsInstance<SongItem>()
            ?.firstOrNull() ?: continue
        resolved += found
      }
      if (resolved.isEmpty()) return@withContext

      val existing = runCatching {
        database.playlistsByNameAsc().first().firstOrNull { it.playlist.name == PLAYLIST_NAME }
      }
        .getOrNull()

      val playlistId: String
      if (existing != null) {
        playlistId = existing.playlist.id
        database.clearPlaylist(playlistId)
      } else {
        playlistId = "ai_taste_${System.currentTimeMillis()}"
        database.insert(
          PlaylistEntity(
            id = playlistId,
            name = PLAYLIST_NAME,
            isLocal = true,
            isEditable = true,
          ),
        )
      }

      resolved.forEachIndexed { index, song ->
        database.insert(song.toAiMediaMetadata())
        database.insert(
          PlaylistSongMap(playlistId = playlistId, songId = song.id, position = index),
        )
      }
      onLog?.invoke("Added ${resolved.size} recommendations.")
      AiLogger.log(context, "AI recommendations generated", "${resolved.size} songs")
    }

  private fun parseArray(content: String): List<AiGeneratedSong>? {
    val start = content.indexOf('[')
    val end = content.lastIndexOf(']')
    if (start < 0 || end <= start) return null
    return runCatching {
      Json {
          ignoreUnknownKeys = true
          isLenient = true
        }
        .decodeFromString(
          kotlinx.serialization.builtins.ListSerializer(AiGeneratedSong.serializer()),
          content.substring(start, end + 1),
        )
    }
      .getOrNull()
  }
}
