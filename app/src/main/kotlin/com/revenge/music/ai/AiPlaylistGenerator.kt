package com.revenge.music.ai

import android.content.Context
import com.revenge.innertube.YouTube
import com.revenge.innertube.models.SongItem
import com.revenge.lyrics.api.OpenRouterService
import com.revenge.music.ai.weather.WeatherInfo
import com.revenge.core.constants.OpenRouterApiKey
import com.revenge.core.constants.OpenRouterBaseUrlKey
import com.revenge.core.constants.OpenRouterModelKey
import com.revenge.core.db.MusicDatabase
import com.revenge.core.db.entities.PlaylistEntity
import com.revenge.core.db.entities.PlaylistSongMap
import com.revenge.core.models.MediaMetadata
import com.revenge.core.utils.dataStore
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal object AiOpenRouter {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun complete(
        context: Context,
        systemPrompt: String,
        userPrompt: String,
        onLog: (String) -> Unit,
    ): String? = withContext(Dispatchers.IO) {
        val prefs = runCatching { context.dataStore.data.first() }.getOrNull() ?: return@withContext null
        val apiKey = prefs[OpenRouterApiKey].orEmpty()
        if (apiKey.isBlank()) {
            onLog("API Key is missing. Add an OpenRouter key in settings.")
            return@withContext null
        }
        val baseUrl = prefs[OpenRouterBaseUrlKey]
            ?: "https://openrouter.ai/api/v1/chat/completions"
        val model = prefs[OpenRouterModelKey] ?: "openrouter/free"
        if (!OpenRouterService.isUrlSafe(baseUrl)) {
            onLog("Restricted or unsafe AI endpoint destination.")
            return@withContext null
        }

        val bodyJson = buildJsonObject {
            put("model", model)
            put(
                "messages",
                buildJsonArray {
                    add(buildJsonObject { put("role", "system"); put("content", systemPrompt) })
                    add(buildJsonObject { put("role", "user"); put("content", userPrompt) })
                },
            )
        }.toString()

        val request = Request.Builder()
            .url(baseUrl)
            .header("Authorization", "Bearer $apiKey")
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        val text = runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    onLog("AI Request failed: ${response.code}")
                    return@withContext null
                }
                response.body?.string()
            }
        }.getOrElse {
            onLog("AI Request failed: ${it.message}")
            return@withContext null
        } ?: return@withContext null

        val content = runCatching {
            json.parseToJsonElement(text)
                .let { it as kotlinx.serialization.json.JsonObject }
                .get("choices")
                ?.let { it as kotlinx.serialization.json.JsonArray }
                ?.firstOrNull()
                ?.let { it as kotlinx.serialization.json.JsonObject }
                ?.get("message")
                ?.let { it as kotlinx.serialization.json.JsonObject }
                ?.get("content")
                ?.let { it as kotlinx.serialization.json.JsonPrimitive }
                ?.content
        }.getOrNull()
        if (content.isNullOrBlank()) {
            onLog("AI response did not contain content.")
            return@withContext null
        }
        stripFences(content)
    }

    fun stripFences(content: String): String {
        var text = content.trim()
        if (text.startsWith("```")) {
            val firstNewline = text.indexOf('\n')
            if (firstNewline >= 0) text = text.substring(firstNewline + 1)
            if (text.endsWith("```")) text = text.dropLast(3)
        }
        return text.trim()
    }
}

@Serializable
internal data class AiGeneratedSong(val title: String = "", val artist: String = "")

@Serializable
internal data class AiGeneratedPlaylist(
    val name: String = "AI Playlist",
    @SerialName("songs") val songs: List<AiGeneratedSong> = emptyList(),
)

@Singleton
class AiPlaylistGenerator @Inject constructor(
    private val database: MusicDatabase,
) {
    suspend fun generatePlaylist(
        context: Context,
        userPrompt: String,
        numberOfSongs: Int = 15,
        weatherInfo: WeatherInfo? = null,
        onLog: (String) -> Unit = {},
    ): String? = withContext(Dispatchers.IO) {
        val weatherDirective = weatherInfo?.let { weatherAcousticDirective(it) }.orEmpty()
        val system = buildString {
            append("Return only raw JSON of the shape ")
            append("{\"name\":\"...\",\"songs\":[{\"title\":\"...\",\"artist\":\"...\"}]} ")
            append("with exactly $numberOfSongs songs. No markdown, no commentary.")
            if (weatherDirective.isNotBlank()) {
                append(' ')
                append(weatherDirective)
            }
        }
        val user = buildString {
            append("Mood: ").append(userPrompt.trim())
            weatherInfo?.let { append("\nWeather: ").append(it.summaryBadgeText) }
            append("\nDesired length: ").append(numberOfSongs).append(" songs")
        }

        val content = AiOpenRouter.complete(context, system, user, onLog) ?: return@withContext null
        val parsed = parseGeneratedPlaylist(content) ?: run {
            onLog("AI returned unparsable JSON.")
            return@withContext null
        }

        val resolved = ArrayList<SongItem>()
        for (song in parsed.songs.take(numberOfSongs)) {
            if (song.title.isBlank() || song.artist.isBlank()) continue
            val found = YouTube.search(
                "${song.title} ${song.artist}",
                YouTube.SearchFilter.FILTER_SONG,
            ).getOrNull()?.items?.filterIsInstance<SongItem>()?.firstOrNull() ?: continue
            resolved += found
        }
        if (resolved.isEmpty()) {
            onLog("No songs could be resolved.")
            return@withContext null
        }

        val playlistId = "ai_playlist_${System.currentTimeMillis()}"
        val name = parsed.name.ifBlank { "AI Playlist" }
        database.withTransaction {
            database.insert(
                PlaylistEntity(
                    id = playlistId,
                    name = name,
                    radioEndpointParams = weatherInfo?.toSnapshotString(),
                    isLocal = true,
                    isEditable = true,
                ),
            )
            resolved.forEachIndexed { index, song ->
                database.insert(song.toAiMediaMetadata())
                database.insert(
                    PlaylistSongMap(
                        playlistId = playlistId,
                        songId = song.id,
                        position = index,
                    ),
                )
            }
        }
        onLog("Created playlist $name with ${resolved.size} songs.")
        AiLogger.log(context, "AI playlist generated", name)
        playlistId
    }

    private fun parseGeneratedPlaylist(content: String): AiGeneratedPlaylist? {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching {
            Json { ignoreUnknownKeys = true; isLenient = true }
                .decodeFromString(AiGeneratedPlaylist.serializer(), content.substring(start, end + 1))
        }.getOrNull()
    }

    private fun weatherAcousticDirective(info: WeatherInfo): String {
        val condition = info.condition.lowercase()
        return when {
            "rain" in condition || "drizzle" in condition ->
                "Prefer lo-fi and ambient tracks."
            "sun" in condition || "clear" in condition ->
                "Prefer upbeat, bright tracks."
            "fog" in condition || info.temperature < 8 ->
                "Prefer ambient, introspective tracks."
            !info.isDay || "night" in condition ->
                "Prefer synthwave and R&B tracks."
            else -> ""
        }
    }
}

internal fun SongItem.toAiMediaMetadata(): MediaMetadata = MediaMetadata(
    id = id,
    title = title,
    artists = artists.map { MediaMetadata.Artist(it.id, it.name) },
    duration = duration ?: -1,
    thumbnailUrl = thumbnail,
    album = album?.let { MediaMetadata.Album(it.id, it.name) },
    musicVideoType = musicVideoType,
    explicit = explicit,
)
