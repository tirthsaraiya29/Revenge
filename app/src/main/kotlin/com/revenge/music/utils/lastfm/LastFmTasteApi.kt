package com.revenge.music.utils.lastfm

import com.revenge.music.BuildConfig
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class SimilarTrack(
    val name: String,
    val artist: String,
    val match: Double = 0.0,
)

data class SimilarArtist(
    val name: String,
    val match: Double = 0.0,
)

data class TasteTag(
    val name: String,
    val count: Int = 0,
)

data class UserTopArtist(
    val name: String,
    val playCount: Int = 0,
)

data class UserTopTrack(
    val name: String,
    val artist: String,
    val playCount: Int = 0,
)

/** External taste service. Every method returns a failure instead of throwing. */
interface LastFmTasteApi {
    suspend fun getSimilarTracks(artist: String, track: String): Result<List<SimilarTrack>>
    suspend fun getSimilarArtists(artist: String): Result<List<SimilarArtist>>
    suspend fun getTopTags(artist: String): Result<List<TasteTag>>
    suspend fun getUserTopArtists(user: String, limit: Int = 30): Result<List<UserTopArtist>>
    suspend fun getUserTopTracks(user: String, limit: Int = 50): Result<List<UserTopTrack>>
    suspend fun getUserTopTags(user: String, limit: Int = 15): Result<List<TasteTag>>
}

class RealLastFmTasteApi @Inject constructor() : LastFmTasteApi {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun getSimilarTracks(
        artist: String,
        track: String,
    ): Result<List<SimilarTrack>> = runCatching {
        val body = call(
            "track.getsimilar",
            mapOf("artist" to artist, "track" to track, "limit" to "30"),
        ) ?: return Result.success(emptyList())
        body["similartracks"]?.jsonObject?.get("track")?.jsonArray
            .orEmpty().mapNotNull { element ->
                val item = element.jsonObject
                val name = item["name"]?.jsonPrimitive?.contentOrNull
                val itemArtist = item["artist"]?.jsonObject?.get("name")
                    ?.jsonPrimitive?.contentOrNull
                if (name.isNullOrBlank() || itemArtist.isNullOrBlank()) null
                else SimilarTrack(name, itemArtist, item.number("match"))
            }
    }

    override suspend fun getSimilarArtists(artist: String): Result<List<SimilarArtist>> =
        runCatching {
            val body = call("artist.getsimilar", mapOf("artist" to artist, "limit" to "20"))
                ?: return Result.success(emptyList())
            body["similarartists"]?.jsonObject?.get("artist")?.jsonArray
                .orEmpty().mapNotNull { element ->
                    val item = element.jsonObject
                    val name = item["name"]?.jsonPrimitive?.contentOrNull
                    if (name.isNullOrBlank()) null else SimilarArtist(name, item.number("match"))
                }
        }

    override suspend fun getTopTags(artist: String): Result<List<TasteTag>> = runCatching {
        val body = call("artist.gettoptags", mapOf("artist" to artist))
            ?: return Result.success(emptyList())
        body["toptags"]?.jsonObject?.get("tag")?.jsonArray.orEmpty().mapNotNull { element ->
            val item = element.jsonObject
            val name = item["name"]?.jsonPrimitive?.contentOrNull
            if (name.isNullOrBlank()) null else TasteTag(name, item.number("count").toInt())
        }
    }

    override suspend fun getUserTopArtists(
        user: String,
        limit: Int,
    ): Result<List<UserTopArtist>> = runCatching {
        val body = call(
            "user.gettopartists",
            mapOf("user" to user, "period" to "overall", "limit" to limit.toString()),
        ) ?: return Result.success(emptyList())
        body["topartists"]?.jsonObject?.get("artist")?.jsonArray.orEmpty().mapNotNull { element ->
            val item = element.jsonObject
            val name = item["name"]?.jsonPrimitive?.contentOrNull
            if (name.isNullOrBlank()) null else UserTopArtist(name, item.number("playcount").toInt())
        }
    }

    override suspend fun getUserTopTracks(
        user: String,
        limit: Int,
    ): Result<List<UserTopTrack>> = runCatching {
        val body = call(
            "user.gettoptracks",
            mapOf("user" to user, "period" to "overall", "limit" to limit.toString()),
        ) ?: return Result.success(emptyList())
        body["toptracks"]?.jsonObject?.get("track")?.jsonArray.orEmpty().mapNotNull { element ->
            val item = element.jsonObject
            val name = item["name"]?.jsonPrimitive?.contentOrNull
            val artist = item["artist"]?.jsonObject?.get("name")
                ?.jsonPrimitive?.contentOrNull
            if (name.isNullOrBlank() || artist.isNullOrBlank()) null
            else UserTopTrack(name, artist, item.number("playcount").toInt())
        }
    }

    override suspend fun getUserTopTags(
        user: String,
        limit: Int,
    ): Result<List<TasteTag>> = runCatching {
        val body = call(
            "user.gettoptags",
            mapOf("user" to user, "period" to "overall", "limit" to limit.toString()),
        ) ?: return Result.success(emptyList())
        body["toptags"]?.jsonObject?.get("tag")?.jsonArray.orEmpty().mapNotNull { element ->
            val item = element.jsonObject
            val name = item["name"]?.jsonPrimitive?.contentOrNull
            if (name.isNullOrBlank()) null else TasteTag(name, item.number("count").toInt())
        }
    }

    private suspend fun call(method: String, params: Map<String, String>): JsonObject? {
        val apiKey = LastFM.getApiKey().ifBlank { BuildConfig.LASTFM_API_KEY }
        if (apiKey.isBlank()) return null
        val query = buildList {
            add("method" to method)
            add("api_key" to apiKey)
            add("format" to "json")
            params.forEach { (key, value) ->
                if (value.isNotBlank()) add(key to value)
            }
        }.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        val text = runCatching {
            LastFM.getKtorClient().get("${LastFM.BASE_URL}?$query").bodyAsText()
        }.getOrNull() ?: return null
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return null
        if (root["error"] != null) return null
        return root
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    private fun JsonObject.number(key: String): Double {
        val primitive = this[key]?.jsonPrimitive ?: return 0.0
        return primitive.doubleOrNull ?: primitive.contentOrNull?.toDoubleOrNull() ?: 0.0
    }
}
