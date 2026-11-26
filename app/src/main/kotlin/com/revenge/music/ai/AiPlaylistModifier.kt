package com.revenge.music.ai

import android.content.Context
import com.revenge.innertube.YouTube
import com.revenge.innertube.models.SongItem
import com.revenge.music.db.MusicDatabase
import com.revenge.music.db.entities.PlaylistSong
import com.revenge.music.db.entities.PlaylistSongMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class ModifierAdditions(val additions: List<AiGeneratedSong> = emptyList())

@Serializable
private data class ModifierResponse(
    @SerialName("remove_ids") val removeIds: List<Int> = emptyList(),
    val additions: List<AiGeneratedSong> = emptyList(),
)

@Singleton
class AiPlaylistModifier @Inject constructor(
    private val database: MusicDatabase,
) {
    suspend fun modifyPlaylist(
        context: Context,
        playlistId: String,
        currentSongs: List<PlaylistSong>,
        userPrompt: String,
        onLog: (String) -> Unit = {},
    ): Unit = withContext(Dispatchers.IO) {
        val songsPayload = currentSongs.joinToString(",") { entry ->
            """{"id":${entry.map.id},"title":${quote(entry.song.song.title)},"artist":${quote(entry.song.artists.firstOrNull()?.name.orEmpty())}}"""
        }
        val system = "Return only raw JSON {\"remove_ids\":[],\"additions\":[{\"title\":\"...\",\"artist\":\"...\"}]}."
        val user = "Existing songs: [$songsPayload]\nInstruction: ${userPrompt.trim()}"
        val content = AiOpenRouter.complete(context, system, user, onLog) ?: return@withContext
        val parsed = parse(content) ?: run {
            onLog("AI returned unparsable JSON.")
            return@withContext
        }
        if (parsed.removeIds.isEmpty() && parsed.additions.isEmpty()) {
            onLog("No modifications requested by AI.")
            return@withContext
        }

        val byMapId = currentSongs.associateBy { it.map.id }
        for (mapId in parsed.removeIds) {
            val entry = byMapId[mapId] ?: continue
            runCatching { database.delete(entry.map) }
        }

        if (parsed.additions.isNotEmpty()) {
            var position = (currentSongs.maxOfOrNull { it.map.position } ?: -1) + 1
            for (addition in parsed.additions) {
                if (addition.title.isBlank() || addition.artist.isBlank()) continue
                val found = YouTube.search(
                    "${addition.title} ${addition.artist}",
                    YouTube.SearchFilter.FILTER_SONG,
                ).getOrNull()?.items?.filterIsInstance<SongItem>()?.firstOrNull() ?: continue
                database.insert(found.toAiMediaMetadata())
                database.insert(
                    PlaylistSongMap(
                        playlistId = playlistId,
                        songId = found.id,
                        position = position++,
                    ),
                )
            }
        }
        AiLogger.log(context, "AI playlist modified", playlistId)
    }

    private fun parse(content: String): ModifierResponse? {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching {
            Json { ignoreUnknownKeys = true; isLenient = true }
                .decodeFromString(ModifierResponse.serializer(), content.substring(start, end + 1))
        }.getOrNull()
    }

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                else -> append(char)
            }
        }
        append('"')
    }
}
