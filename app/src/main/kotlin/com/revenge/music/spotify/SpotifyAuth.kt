package com.revenge.music.spotify

import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.revenge.music.spotify.models.SpotifyInternalToken

/**
 * Converts a Spotify `sp_dc` cookie into an internal web-player access token using the TOTP
 * handshake. The HTTP and clock access are internal seams so the flow is testable without a
 * network.
 */
object SpotifyAuth {

  const val LOGIN_URL =
    "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F"

  private const val GIST_URL = "https://api.github.com/gists/22ed9c6ba463899e933427f7de1f0eef"
  private const val TOKEN_URL =
    "https://open.spotify.com/api/token?reason=transport&productType=web-player"
  private const val SERVER_TIME_URL = "https://open.spotify.com/api/server-time"
  // nosemgrep: revenge-hardcoded-credential -- public Spotify web-player TOTP secret, not a user credential.
  private const val DEFAULT_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
  private const val DEFAULT_VERSION = "5"
  private const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
      "Chrome/126.0.0.0 Safari/537.36"

  internal data class HttpResult(val status: Int, val body: String)

  /** `(url, headers) -> response`; replaced by deterministic fakes in tests. */
  internal var http: suspend (String, Map<String, String>) -> HttpResult = { url, headers ->
    realGet(url, headers)
  }

  internal var deviceEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000L }

  private val nuanceSecret = AtomicReference<String?>(null)
  private val nuanceVersion = AtomicReference<String?>(null)
  private val serverOffsetSeconds = AtomicLong(0)

  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  suspend fun fetchAccessToken(
    spDc: String,
    spKey: String = "",
    forceRefreshNuance: Boolean = false,
  ): Result<SpotifyInternalToken> =
    try {
      if (spDc.isBlank()) {
        throw Spotify.SpotifyException(401, "Missing sp_dc cookie")
      }
      val (secret, version) = resolveSecret(forceRefreshNuance)
      val serverTime = resolveServerTime()
      val counter = (serverTime / 30L)
      val totp = generateTotp(secret, counter)
      val cookie = buildString {
        append("sp_dc=")
        append(spDc)
        if (spKey.isNotBlank()) {
          append("; sp_key=")
          append(spKey)
        }
      }
      val url =
        "$TOKEN_URL&totp=$totp&totpServer=$totp&totpVer=${encode(version)}"
      val response =
        http(
          url,
          mapOf(
            "Cookie" to cookie,
            "User-Agent" to USER_AGENT,
            "Accept" to "application/json",
            "Accept-Language" to "en-US,en;q=0.9",
          ),
        )
      if (response.status !in 200..299) {
        throw Spotify.SpotifyException(response.status, "HTTP ${response.status}: ${response.body}")
      }
      val token = parseToken(response.body)
      if (!token.isAnonymous && token.accessToken.isNotBlank()) {
        Result.success(token)
      } else if (!forceRefreshNuance) {
        fetchAccessToken(spDc, spKey, forceRefreshNuance = true)
      } else {
        Result.failure(
          Spotify.SpotifyException(
            401,
            "Received anonymous token — sp_dc cookie is invalid or expired",
          ),
        )
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Spotify.SpotifyException) {
      Result.failure(e)
    } catch (e: Throwable) {
      Result.failure(
        e as? Spotify.SpotifyException
          ?: Spotify.SpotifyException(0, e.message ?: "Token request failed"),
      )
    }

  internal fun parseToken(body: String): SpotifyInternalToken {
    val obj = json.parseToJsonElement(body).jsonObject
    return SpotifyInternalToken(
      accessToken =
        (obj["accessToken"] as? JsonPrimitive)?.content
          ?: (obj["access_token"] as? JsonPrimitive)?.content
          ?: "",
      accessTokenExpirationTimestampMs =
        (obj["accessTokenExpirationTimestampMs"] as? JsonPrimitive)?.content?.toLongOrNull()
          ?: (obj["expires_at"] as? JsonPrimitive)?.content?.toLongOrNull()
          ?: 0L,
      isAnonymous = (obj["isAnonymous"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false,
      clientId = (obj["clientId"] as? JsonPrimitive)?.content.orEmpty(),
    )
  }

  private suspend fun resolveSecret(forceRefresh: Boolean): Pair<String, String> {
    val cached = nuanceSecret.get()
    if (!forceRefresh) {
      return (cached ?: DEFAULT_SECRET) to (nuanceVersion.get() ?: DEFAULT_VERSION)
    }
    val refreshed = fetchNuance()
    if (refreshed != null) {
      nuanceSecret.set(refreshed.first)
      nuanceVersion.set(refreshed.second)
      return refreshed
    }
    return (cached ?: DEFAULT_SECRET) to (nuanceVersion.get() ?: DEFAULT_VERSION)
  }

  private suspend fun fetchNuance(): Pair<String, String>? =
    try {
      val response = http(GIST_URL, mapOf("Accept" to "application/vnd.github+json"))
      if (response.status !in 200..299) null
      else parseNuance(response.body)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      null
    }

  internal fun parseNuance(body: String): Pair<String, String>? {
    val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return null
    val files = root.jsonObject["files"]?.jsonObject ?: return null
    val entries = mutableListOf<Triple<Int, String, String>>()
    for (file in files.values) {
      val content = file.jsonObject["content"]?.jsonPrimitive?.content ?: continue
      val parsed = runCatching { json.parseToJsonElement(content) as? JsonArray }.getOrNull()
        ?: continue
      parsed.forEach { element ->
        val obj = element as? JsonObject ?: return@forEach
        val secret = (obj["s"] as? JsonPrimitive)?.content ?: return@forEach
        if (!isBase32(secret)) return@forEach
        val version = (obj["v"] as? JsonPrimitive)?.intOrNull ?: 0
        entries += Triple(version, secret, version.toString())
      }
    }
    val best = entries.maxByOrNull { it.first } ?: return null
    return best.second to best.third
  }

  private suspend fun resolveServerTime(): Long {
    val device = deviceEpochSeconds()
    val server =
      try {
        val response = http(SERVER_TIME_URL, mapOf("Accept" to "application/json"))
        if (response.status in 200..299) {
          val obj = json.parseToJsonElement(response.body).jsonObject
          (obj["serverTime"] as? JsonPrimitive)?.content?.toLongOrNull()
        } else null
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        null
      }
    if (server != null) {
      serverOffsetSeconds.set(server - device)
      return server
    }
    return device + serverOffsetSeconds.get()
  }

  /** RFC 6238 compatible TOTP, SHA-1, 30-second step, 6 digits. */
  internal fun generateTotp(secretBase32: String, counter: Long, digits: Int = 6): String {
    val key = base32Decode(secretBase32)
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(SecretKeySpec(key, "HmacSHA1"))
    val message = ByteArray(8) { i -> ((counter ushr ((7 - i) * 8)) and 0xff).toByte() }
    val hash = mac.doFinal(message)
    val offset = hash[hash.size - 1].toInt() and 0x0f
    val binary =
      ((hash[offset].toInt() and 0x7f) shl 24) or
        ((hash[offset + 1].toInt() and 0xff) shl 16) or
        ((hash[offset + 2].toInt() and 0xff) shl 8) or
        (hash[offset + 3].toInt() and 0xff)
    val modulus = Math.pow(10.0, digits.toDouble()).toInt()
    return (binary % modulus).toString().padStart(digits, '0')
  }

  internal fun base32Decode(value: String): ByteArray {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    val cleaned = value.uppercase().filter { it in alphabet }
    var buffer = 0
    var bits = 0
    val out = java.io.ByteArrayOutputStream()
    for (c in cleaned) {
      buffer = (buffer shl 5) or alphabet.indexOf(c)
      bits += 5
      if (bits >= 8) {
        bits -= 8
        out.write((buffer ushr bits) and 0xff)
      }
    }
    return out.toByteArray()
  }

  private fun isBase32(value: String): Boolean {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567="
    return value.isNotEmpty() && value.uppercase().all { it in alphabet }
  }

  private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

  private suspend fun realGet(url: String, headers: Map<String, String>): HttpResult =
    withContext(Dispatchers.IO) {
      val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 15_000
        readTimeout = 15_000
        useCaches = false
        headers.forEach { (k, v) -> setRequestProperty(k, v) }
      }
      try {
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        HttpResult(status, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
      } finally {
        connection.disconnect()
      }
    }
}
