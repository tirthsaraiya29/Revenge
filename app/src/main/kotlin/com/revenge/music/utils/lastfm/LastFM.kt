package com.revenge.music.utils.lastfm

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Last.fm API facade. Every network operation is wrapped in [runCatching]. */
object LastFM {
    const val BASE_URL = "https://ws.audioscrobbler.com/2.0/"

    @Volatile private var apiKey: String = ""
    @Volatile private var secret: String = ""
    @Volatile var sessionKey: String? = null

    private val jsonFormat = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client: HttpClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(jsonFormat) }
        }
    }

    fun initialize(apiKey: String, secret: String) {
        this.apiKey = apiKey.trim()
        this.secret = secret.trim()
    }

    fun isInitialized(): Boolean = apiKey.isNotBlank() && secret.isNotBlank()

    fun getApiKey(): String = apiKey

    fun getKtorClient(): HttpClient = client

    suspend fun getToken(): Result<String> = runCatching {
        val body = perform("auth.getToken", emptyMap(), withSession = false)
            ?: error("Last.fm token request failed")
        body["token"]?.jsonPrimitive?.contentOrNull ?: error("Missing token")
    }

    suspend fun getSession(token: String): Result<String> = runCatching {
        val body = perform("auth.getSession", mapOf("token" to token), withSession = false)
            ?: error("Last.fm session request failed")
        val key = body["session"]?.jsonObject?.get("key")
            ?.jsonPrimitive?.contentOrNull ?: error("Missing session key")
        sessionKey = key
        key
    }

    suspend fun getMobileSession(username: String, password: String): Result<String> = runCatching {
        val body = perform(
            "auth.getMobileSession",
            mapOf("username" to username, "password" to password),
            withSession = false,
        ) ?: error("Last.fm mobile session request failed")
        val key = body["session"]?.jsonObject?.get("key")
            ?.jsonPrimitive?.contentOrNull ?: error("Missing session key")
        sessionKey = key
        key
    }

    suspend fun updateNowPlaying(
        artist: String,
        track: String,
        album: String? = null,
        duration: Int? = null,
    ): Result<Unit> = runCatching {
        val params = buildMap {
            put("artist", artist)
            put("track", track)
            album?.takeIf { it.isNotBlank() }?.let { put("album", it) }
            duration?.takeIf { it > 0 }?.let { put("duration", it.toString()) }
        }
        perform("track.updateNowPlaying", params) ?: error("Now playing update failed")
        Unit
    }

    suspend fun scrobble(
        artist: String,
        track: String,
        timestamp: Long,
        album: String? = null,
        duration: Int? = null,
    ): Result<Unit> = runCatching {
        val params = buildMap {
            put("artist", artist)
            put("track", track)
            put("timestamp", timestamp.toString())
            album?.takeIf { it.isNotBlank() }?.let { put("album", it) }
            duration?.takeIf { it > 0 }?.let { put("duration", it.toString()) }
        }
        perform("track.scrobble", params) ?: error("Scrobble failed")
        Unit
    }

    suspend fun love(artist: String, track: String): Result<Unit> = runCatching {
        perform("track.love", mapOf("artist" to artist, "track" to track))
            ?: error("Love failed")
        Unit
    }

    suspend fun unlove(artist: String, track: String): Result<Unit> = runCatching {
        perform("track.unlove", mapOf("artist" to artist, "track" to track))
            ?: error("Unlove failed")
        Unit
    }

    internal fun signature(params: Map<String, String>, secret: String): String {
        val concatenated = params.toSortedMap()
            .entries
            .joinToString(separator = "") { (key, value) -> key + value }
        return md5(concatenated + secret)
    }

    private fun md5(value: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private suspend fun perform(
        method: String,
        params: Map<String, String>,
        withSession: Boolean = true,
    ): JsonObject? = runCatching {
        val key = apiKey
        if (key.isBlank()) return null
        val all = LinkedHashMap<String, String>()
        all["method"] = method
        all["api_key"] = key
        if (withSession) {
            sessionKey?.takeIf { it.isNotBlank() }?.let { all["sk"] = it }
        }
        params.forEach { (paramKey, value) ->
            if (value.isNotBlank()) all[paramKey] = value
        }
        all["api_sig"] = signature(all, secret)
        all["format"] = "json"

        val form = Parameters.build {
            all.forEach { (k, v) -> append(k, v) }
        }
        val text = client.submitForm(BASE_URL, form).bodyAsText()
        val root = jsonFormat.parseToJsonElement(text).jsonObject
        if (root["error"] != null) null else root
    }.getOrNull()
}
