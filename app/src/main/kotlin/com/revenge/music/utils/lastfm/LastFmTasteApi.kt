package com.revenge.music.utils.lastfm

import com.revenge.music.BuildConfig
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import timber.log.Timber

data class SimilarTrack(
    val name: String,
    val artist: String,
    val match: Double? = null,
)

data class SimilarArtist(
    val name: String,
    val match: Double? = null,
)

data class Tag(
    val name: String,
    val count: Int = 0,
)

interface LastFmTasteApi {
    suspend fun getSimilarTracks(artist: String, track: String): Result<List<SimilarTrack>>
    suspend fun getSimilarArtists(artist: String): Result<List<SimilarArtist>>
    suspend fun getTopTags(artist: String): Result<List<Tag>>
    suspend fun getUserTopArtists(user: String, limit: Int = 30): Result<List<String>>
    suspend fun getUserTopTracks(user: String, limit: Int = 50): Result<List<SimilarTrack>>
    suspend fun getUserTopTags(user: String, limit: Int = 15): Result<List<Tag>>
}

/**
 * Thin read-only Last.fm API used by the recommendations engine. Any malformed or failed response
 * degrades to an empty list; only a transport failure escapes as [Result.failure].
 */
class RealLastFmTasteApi @Inject constructor() : LastFmTasteApi {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    private val apiKey: String
        get() = LastFM.getApiKey().ifBlank { BuildConfig.LASTFM_API_KEY }

    override suspend fun getSimilarTracks(artist: String, track: String): Result<List<SimilarTrack>> {
        if (artist.isBlank() || track.isBlank()) return Result.success(emptyList())
        return query(
            "track.getsimilar",
            mapOf("artist" to artist, "track" to track, "limit" to "30"),
            path = listOf("similartracks", "track"),
        ) { element ->
            val name = element.string("name") ?: return@query null
            val artistName = element.artistName() ?: return@query null
            SimilarTrack(name = name, artist = artistName, match = element.number("match"))
        }
    }

    override suspend fun getSimilarArtists(artist: String): Result<List<SimilarArtist>> {
        if (artist.isBlank()) return Result.success(emptyList())
        return query(
            "artist.getsimilar",
            mapOf("artist" to artist, "limit" to "20"),
            path = listOf("similarartists", "artist"),
        ) { element ->
            val name = element.string("name") ?: return@query null
            SimilarArtist(name = name, match = element.number("match"))
        }
    }

    override suspend fun getTopTags(artist: String): Result<List<Tag>> {
        if (artist.isBlank()) return Result.success(emptyList())
        return query(
            "artist.gettoptags",
            mapOf("artist" to artist),
            path = listOf("toptags", "tag"),
        ) { it.toTag() }
    }

    override suspend fun getUserTopArtists(user: String, limit: Int): Result<List<String>> {
        if (user.isBlank()) return Result.success(emptyList())
        return query(
            "user.gettopartists",
            mapOf("user" to user, "limit" to limit.toString(), "period" to "overall"),
            path = listOf("topartists", "artist"),
        ) { it.string("name") }
    }

    override suspend fun getUserTopTracks(user: String, limit: Int): Result<List<SimilarTrack>> {
        if (user.isBlank()) return Result.success(emptyList())
        return query(
            "user.gettoptracks",
            mapOf("user" to user, "limit" to limit.toString(), "period" to "overall"),
            path = listOf("toptracks", "track"),
        ) { element ->
            val name = element.string("name") ?: return@query null
            val artistName = element.artistName() ?: return@query null
            SimilarTrack(name = name, artist = artistName, match = element.number("match"))
        }
    }

    override suspend fun getUserTopTags(user: String, limit: Int): Result<List<Tag>> {
        if (user.isBlank()) return Result.success(emptyList())
        return query(
            "user.gettoptags",
            mapOf("user" to user, "limit" to limit.toString()),
            path = listOf("toptags", "tag"),
        ) { it.toTag() }
    }

    private suspend fun <T> query(
        method: String,
        parameters: Map<String, String>,
        path: List<String>,
        map: (JsonObject) -> T?,
    ): Result<List<T>> {
        if (apiKey.isBlank()) return Result.success(emptyList())
        return try {
            val response = LastFM.getKtorClient().get(LastFM.BASE_URL) {
                parameter("method", method)
                parameter("format", "json")
                parameter("api_key", apiKey)
                parameters.forEach { (key, value) -> parameter(key, value) }
            }
            val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
            if (root.containsKey("error")) return Result.success(emptyList())
            val container = path.firstOrNull()?.let { root[it] } as? JsonObject
            val items = container?.get(path.getOrNull(1) ?: "") ?: return Result.success(emptyList())
            val list = when (items) {
                is JsonArray -> items.mapNotNull { it as? JsonObject }
                is JsonObject -> listOf(items)
                else -> emptyList()
            }
            Result.success(list.mapNotNull(map))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Timber.w(error, "Last.fm taste query $method failed")
            Result.failure(error)
        }
    }

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.number(name: String): Double? =
        (this[name] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

    private fun JsonObject.artistName(): String? {
        val artist = this["artist"] ?: return null
        return when (artist) {
            is JsonObject -> artist.string("name")
            is JsonPrimitive -> artist.contentOrNull?.takeIf { it.isNotBlank() }
            else -> null
        }
    }

    private fun JsonObject.toTag(): Tag? {
        val name = string("name") ?: return null
        val count = (this["count"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
        return Tag(name = name, count = count)
    }
}
