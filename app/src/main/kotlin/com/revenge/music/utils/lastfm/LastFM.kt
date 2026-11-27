package com.revenge.music.utils.lastfm

import com.revenge.core.models.lastfm.Authentication
import com.revenge.core.models.lastfm.TokenResponse
import com.revenge.music.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.serialization.kotlinx.json.json
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Last.fm 2.0 client. Requests are form-encoded and signed with `api_sig`, the lowercase MD5 of
 * the sorted `key+value` concatenation followed by the API secret.
 */
object LastFM {

    const val BASE_URL = "https://ws.audioscrobbler.com/2.0/"
    const val DEFAULT_SCROBBLE_DELAY_PERCENT = 0.5f
    const val DEFAULT_SCROBBLE_MIN_SONG_DURATION = 30
    const val DEFAULT_SCROBBLE_DELAY_SECONDS = 180

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    private var apiKey: String = ""
    private var secret: String = ""
    private var client: HttpClient? = null

    @Volatile
    var sessionKey: String? = null

    fun initialize(apiKey: String, secret: String) {
        this.apiKey = apiKey
        this.secret = secret
    }

    fun isInitialized(): Boolean = apiKey.isNotEmpty() && secret.isNotEmpty()

    fun getApiKey(): String = apiKey

    fun getKtorClient(): HttpClient = client ?: synchronized(this) {
        client ?: HttpClient(OkHttp) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(json)
            }
        }.also { client = it }
    }

    suspend fun getToken(): Result<TokenResponse> = requestParsed("auth.getToken") { body ->
        json.decodeFromString(TokenResponse.serializer(), body)
    }

    suspend fun getSession(token: String): Result<Authentication> =
        requestParsed("auth.getSession", mapOf("token" to token)) { body ->
            json.decodeFromString(Authentication.serializer(), body)
        }

    fun getAuthUrl(token: String): String =
        "https://www.last.fm/api/auth/?api_key=$apiKey&token=$token"

    suspend fun getMobileSession(username: String, password: String): Result<Authentication> = call(
        "auth.getMobileSession",
        mapOf("username" to username, "password" to password),
    ) { response ->
        val body = response.bodyAsText()
        val root = json.parseToJsonElement(body).jsonObject
        val error = root["error"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        if (error != null) {
            val message = root["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
            throw LastFmException(error, message)
        }
        json.decodeFromString(Authentication.serializer(), body)
    }

    suspend fun updateNowPlaying(
        artist: String,
        track: String,
        album: String? = null,
        trackNumber: Int? = null,
        duration: Int? = null,
    ): Result<HttpResponse> = call(
        "track.updateNowPlaying",
        buildMap {
            put("artist", artist)
            put("track", track)
            album?.let { put("album", it) }
            trackNumber?.let { put("trackNumber", it.toString()) }
            duration?.let { put("duration", it.toString()) }
        },
        authenticated = true,
    ) { it }

    suspend fun scrobble(
        artist: String,
        track: String,
        timestamp: Long,
        album: String? = null,
        trackNumber: Int? = null,
        duration: Int? = null,
    ): Result<HttpResponse> = call(
        "track.scrobble",
        buildMap {
            put("artist[0]", artist)
            put("track[0]", track)
            put("timestamp[0]", timestamp.toString())
            album?.let { put("album[0]", it) }
            trackNumber?.let { put("trackNumber[0]", it.toString()) }
            duration?.let { put("duration[0]", it.toString()) }
        },
        authenticated = true,
    ) { it }

    suspend fun setLoveStatus(artist: String, track: String, love: Boolean): Result<HttpResponse> = call(
        if (love) "track.love" else "track.unlove",
        mapOf("artist" to artist, "track" to track),
        authenticated = true,
    ) { it }

    private suspend fun <T> requestParsed(
        method: String,
        extras: Map<String, String> = emptyMap(),
        parse: (String) -> T,
    ): Result<T> = call(method, extras) { parse(it.bodyAsText()) }

    private suspend fun <T> call(
        method: String,
        extras: Map<String, String> = emptyMap(),
        authenticated: Boolean = false,
        parse: suspend (HttpResponse) -> T,
    ): Result<T> = try {
        val unsigned = LinkedHashMap<String, String>()
        unsigned["method"] = method
        if (apiKey.isNotEmpty()) unsigned["api_key"] = apiKey
        if (authenticated) {
            sessionKey?.let { unsigned["sk"] = it }
        }
        unsigned.putAll(extras)
        val signature = signature(unsigned)
        unsigned["api_sig"] = signature
        unsigned["format"] = "json"

        val response = getKtorClient().post(BASE_URL) {
            setBody(
                FormDataContent(
                    Parameters.build {
                        unsigned.forEach { (key, value) -> append(key, value) }
                    },
                ),
            )
        }
        Result.success(parse(response))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Result.failure(error)
    }

    private fun signature(params: Map<String, String>): String {
        val concatenated = params.entries
            .sortedBy { it.key }
            .joinToString(separator = "") { it.key + it.value }
        return md5(concatenated + secret)
    }

    private fun md5(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { "%02x".format(it) }

    private fun JsonObject.errorMessage(): String =
        this["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
}

class LastFmException(val code: Int, override val message: String) : Exception(message)
