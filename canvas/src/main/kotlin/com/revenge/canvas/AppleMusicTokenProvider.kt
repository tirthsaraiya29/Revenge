package com.revenge.canvas

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Supplies the JWT that the Apple Music web player uses for unauthenticated catalog reads.
 *
 * A live token is scraped from the web player's JavaScript bundle when possible; otherwise a
 * bundled public token is used. Tokens are cached until shortly before they expire and calls are
 * serialized so concurrent callers share one fetch.
 */
object AppleMusicTokenProvider {
  private const val FALLBACK_TOKEN =
    "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiIsImtpZCI6IldlYlBsYXlLaWQifQ.eyJpc3MiOiJBTVBXZWJQbGF5IiwiaWF0IjoxNzkzOTAwNzA4LCJleHAiOjE3OTk5NDg3MDgsInJvb3RfaHR0cHNfb3JpZ2luIjpbImFwcGxlLmNvbSJdfQ.7L-PYJHjO8PpFdhKS-OSkf_snyFvg5cq-JDERUs_N_UcUX9Vk0kf1WLatgDeEvhB_Arg1b5Tl-99G1v6hUwH0A"

  private const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

  // Refresh a little early so a token does not expire mid-request.
  private const val EXPIRY_HEADROOM_MS = 5L * 60 * 1000
  private const val FALLBACK_TTL_MS = 24L * 60 * 60 * 1000

  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  private val httpClient by lazy {
    HttpClient(OkHttp) {
      install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 15_000
        socketTimeoutMillis = 15_000
      }
      expectSuccess = false
    }
  }

  private val mutex = Mutex()

  @Volatile private var cachedToken: String? = null
  @Volatile private var cachedExpiryMs: Long = 0L

  suspend fun getToken(): String =
    mutex.withLock {
      val now = System.currentTimeMillis()
      cachedToken?.takeIf { cachedExpiryMs > now + EXPIRY_HEADROOM_MS }?.let { return it }

      val live = fetchLiveTokenOrNull()
      if (live != null) {
        cachedToken = live.value
        cachedExpiryMs = live.expiresAtMs
        return live.value
      }

      cachedToken = FALLBACK_TOKEN
      cachedExpiryMs = now + FALLBACK_TTL_MS
      FALLBACK_TOKEN
    }

  suspend fun clearToken() {
    mutex.withLock {
      cachedToken = null
      cachedExpiryMs = 0L
    }
  }

  private data class LiveToken(val value: String, val expiresAtMs: Long)

  private suspend fun fetchLiveTokenOrNull(): LiveToken? =
    try {
      discoverLiveToken()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      null
    }

  private suspend fun discoverLiveToken(): LiveToken? {
    for (pageUrl in listOf("https://music.apple.com/us/browse", "https://beta.music.apple.com")) {
      val html = fetchText(pageUrl, acceptHtml = true) ?: continue
      val bundlePath = BUNDLE_PATTERN.find(html)?.value ?: continue
      val bundleUrl =
        if (bundlePath.startsWith("http")) bundlePath else "https://music.apple.com$bundlePath"

      val script = fetchText(bundleUrl, acceptHtml = false) ?: continue
      val threshold = System.currentTimeMillis() + EXPIRY_HEADROOM_MS
      val best =
        JWT_PATTERN
          .findAll(script)
          .mapNotNull { decodeToken(it.value) }
          .filter { it.expiresAtMs > threshold }
          .maxByOrNull { it.expiresAtMs }
      if (best != null) return best
    }
    return null
  }

  private suspend fun fetchText(url: String, acceptHtml: Boolean): String? {
    val response =
      httpClient.get(url) {
        header("User-Agent", USER_AGENT)
        if (acceptHtml) header("Accept", "text/html")
      }
    if (response.status.value !in 200..299) return null
    return response.bodyAsText()
  }

  private fun decodeToken(raw: String): LiveToken? =
    try {
      val parts = raw.split('.')
      if (parts.size != 3) return null
      val payload =
        json.parseToJsonElement(String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8))
          .jsonObject
      val issuer = payload["iss"]?.jsonPrimitive?.contentOrNull
      val expiresAtMs =
        payload["exp"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()?.times(1000L) ?: 0L
      if (issuer == "AMPWebPlay") LiveToken(raw, expiresAtMs) else null
    } catch (e: Exception) {
      null
    }

  private val BUNDLE_PATTERN = Regex("""/assets/index[^"'\s]*\.js""")

  private val JWT_PATTERN = Regex("""eyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")
}
