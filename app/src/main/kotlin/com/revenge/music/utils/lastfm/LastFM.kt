package com.revenge.music.utils.lastfm

import com.revenge.core.models.lastfm.Authentication
import com.revenge.core.models.lastfm.LastFmError
import com.revenge.core.models.lastfm.TokenResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class LastFmException(val code: Int, override val message: String) : Exception(message)

object LastFM {

  const val DEFAULT_SCROBBLE_DELAY_PERCENT = 0.5f
  const val DEFAULT_SCROBBLE_MIN_SONG_DURATION = 30
  const val DEFAULT_SCROBBLE_DELAY_SECONDS = 180

  private const val BASE_URL = "https://ws.audioscrobbler.com/2.0/"
  private const val USER_AGENT = "Revenge/1.0 (Android)"

  @Volatile private var apiKey: String = ""
  @Volatile private var apiSecret: String = ""

  @Volatile var sessionKey: String? = null

  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  private val ktor by lazy { HttpClient(OkHttp) }

  /** Test seam: replaces the form POST with a canned response. */
  internal var transport: suspend (Map<String, String>) -> Result<String> = { params ->
    postForm(params)
  }

  fun initialize(apiKey: String, secret: String) {
    this.apiKey = apiKey
    this.apiSecret = secret
  }

  fun isInitialized(): Boolean = apiKey.isNotEmpty() && apiSecret.isNotEmpty()

  fun getApiKey(): String = apiKey

  fun getKtorClient(): HttpClient = ktor

  suspend fun getToken(): Result<TokenResponse> =
    signed(mapOf("method" to "auth.getToken")).mapCatching { body ->
      val obj = json.parseToJsonElement(body).jsonObject
      TokenResponse(obj["token"]?.jsonPrimitive?.content.orEmpty())
    }

  suspend fun getSession(token: String): Result<Authentication> =
    signed(mapOf("method" to "auth.getSession", "token" to token)).mapCatching { body ->
      json.decodeFromString<Authentication>(body)
    }

  fun getAuthUrl(token: String): String =
    "https://www.last.fm/api/auth/?api_key=${encode(apiKey)}&token=${encode(token)}"

  suspend fun getMobileSession(
    username: String,
    password: String,
  ): Result<Authentication> =
    signed(
        mapOf(
          "method" to "auth.getMobileSession",
          "username" to username,
          "password" to password,
        ),
      )
      .mapCatching { body ->
        val obj = json.parseToJsonElement(body).jsonObject
        if (obj.containsKey("error")) {
          val error = json.decodeFromString<LastFmError>(body)
          throw LastFmException(error.error, error.message)
        }
        json.decodeFromString<Authentication>(body)
      }

  suspend fun updateNowPlaying(
    artist: String,
    track: String,
    album: String? = null,
    trackNumber: Int? = null,
    duration: Int? = null,
  ): Result<String> =
    signed(
      buildMap {
        put("method", "track.updateNowPlaying")
        put("artist", artist)
        put("track", track)
        album?.let { put("album", it) }
        trackNumber?.let { put("trackNumber", it.toString()) }
        duration?.let { put("duration", it.toString()) }
        sessionKey?.let { put("sk", it) }
      },
    )

  suspend fun scrobble(
    artist: String,
    track: String,
    timestamp: Long,
    album: String? = null,
    trackNumber: Int? = null,
    duration: Int? = null,
  ): Result<String> =
    signed(
      buildMap {
        put("method", "track.scrobble")
        put("artist[0]", artist)
        put("track[0]", track)
        put("timestamp[0]", timestamp.toString())
        album?.let { put("album[0]", it) }
        trackNumber?.let { put("trackNumber[0]", it.toString()) }
        duration?.let { put("duration[0]", it.toString()) }
        sessionKey?.let { put("sk", it) }
      },
    )

  suspend fun setLoveStatus(
    artist: String,
    track: String,
    love: Boolean,
  ): Result<String> =
    signed(
      buildMap {
        put("method", if (love) "track.love" else "track.unlove")
        put("artist", artist)
        put("track", track)
        sessionKey?.let { put("sk", it) }
      },
    )

  internal fun sign(params: Map<String, String>, secret: String): String {
    val payload =
      params.toSortedMap().entries.joinToString("") { (key, value) -> key + value } + secret
    return md5Hex(payload)
  }

  internal fun signedForm(params: Map<String, String>): Map<String, String> {
    val signature = sign(params, apiSecret)
    return params + ("api_sig" to signature) + ("format" to "json")
  }

  private suspend fun signed(params: Map<String, String>): Result<String> {
    if (!isInitialized()) return Result.failure(Exception("Last.fm is not initialized"))
    val complete = buildMap {
      put("method", params["method"].orEmpty())
      put("api_key", apiKey)
      params.filterKeys { it != "method" }.forEach { (key, value) -> put(key, value) }
    }
    val form = signedForm(complete)
    return transport(form)
  }

  private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

  private suspend fun postForm(params: Map<String, String>): Result<String> =
    try {
      withContext(Dispatchers.IO) {
        val body =
          params.entries.joinToString("&") { (key, value) ->
            encode(key) + "=" + encode(value)
          }
        val connection =
          (URI(BASE_URL).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            useCaches = false
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("User-Agent", USER_AGENT)
          }
        try {
          connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
          val code = connection.responseCode
          val stream = if (code in 200..299) connection.inputStream else connection.errorStream
          val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
          if (code !in 200..299) Result.failure(Exception("HTTP $code: $text"))
          else Result.success(text)
        } finally {
          connection.disconnect()
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      Result.failure(e)
    }

  internal fun md5Hex(value: String): String {
    val bytes = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
  }
}
