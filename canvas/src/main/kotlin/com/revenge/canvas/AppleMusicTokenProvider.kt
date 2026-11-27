package com.revenge.canvas

import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Process-wide Apple Music web bearer token: scraped from the public web-player bundle, cached
 * until shortly before expiry, and backed by a bundled deployment token when discovery fails.
 */
object AppleMusicTokenProvider {
  private const val HEADROOM_MS = 300_000L
  private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
  private const val AMP_ISSUER = "AMPWebPlay"

  private val discoveryLock = Mutex()
  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
  }

  @Volatile private var cachedToken: String? = null
  @Volatile private var cachedExpiryMs: Long = 0L

  @Volatile internal var transport: CanvasTransport = ktorCanvasTransport(10, 15, 15)
  @Volatile internal var clock: () -> Long = System::currentTimeMillis

  internal fun resetForTests() {
    cachedToken = null
    cachedExpiryMs = 0L
    transport = ktorCanvasTransport(10, 15, 15)
    clock = System::currentTimeMillis
  }

  suspend fun getToken(): String = discoveryLock.withLock {
    val existing = cachedToken
    if (existing != null && cachedExpiryMs > clock() + HEADROOM_MS) return existing

    val discovered = discover()
    if (discovered != null) {
      cachedToken = discovered.first
      cachedExpiryMs = discovered.second
      return discovered.first
    }

    // No valid deployment token is available: a synthetic JWT would only produce
    // guaranteed 401s and a clear/retry loop. Callers catch this and degrade to null.
    throw IllegalStateException("Apple Music bearer token discovery failed")
  }

  suspend fun clearToken() {
    discoveryLock.withLock {
      cachedToken = null
      cachedExpiryMs = 0L
    }
  }

  private suspend fun discover(): Pair<String, Long>? {
    val now = clock()
    var best: Pair<String, Long>? = null
    for (pageUrl in listOf("https://music.apple.com/us/browse", "https://beta.music.apple.com")) {
      try {
        val page =
          transport.get(
            pageUrl,
            emptyList(),
            mapOf("Accept" to "text/html", "User-Agent" to DESKTOP_UA),
          )
        if (page.status != 200) continue

        val bundlePath =
          ABSOLUTE_BUNDLE_PATTERN.find(page.body)?.value
            ?: RELATIVE_BUNDLE_PATTERN.find(page.body)?.value
            ?: continue
        val bundleUrl =
          if (bundlePath.startsWith("http")) bundlePath else "https://music.apple.com$bundlePath"

        val bundle = transport.get(bundleUrl, emptyList(), mapOf("User-Agent" to DESKTOP_UA))
        if (bundle.status != 200) continue

        for (token in TOKEN_PATTERN.findAll(bundle.body).map { it.value }) {
          val expiry = expiryOf(token) ?: continue
          if (expiry <= now + HEADROOM_MS) continue
          val current = best
          if (current == null || expiry > current.second) best = token to expiry
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // A failed source is skipped; discovery as a whole may still succeed.
      }
    }
    return best
  }

  private fun expiryOf(token: String): Long? {
    val parts = token.split('.')
    if (parts.size != 3) return null
    return try {
      val payloadText = String(Base64.getUrlDecoder().decode(padded(parts[1])), Charsets.UTF_8)
      val payload = json.parseToJsonElement(payloadText) as? JsonObject ?: return null
      if (stringValue(payload["iss"]) != AMP_ISSUER) return null
      val seconds = stringValue(payload["exp"])?.toLongOrNull() ?: return null
      seconds * 1000L
    } catch (e: Exception) {
      null
    }
  }

  private fun padded(value: String): String = value + "=".repeat((4 - value.length % 4) % 4)

  private fun stringValue(element: JsonElement?): String? =
    (element as? JsonPrimitive)?.contentOrNull

  private val ABSOLUTE_BUNDLE_PATTERN = Regex("""https?://[^"'\s>]*?/assets/index[^"'\s>]*?\.js""")
  private val RELATIVE_BUNDLE_PATTERN = Regex("""/assets/index[^"'\s>]*?\.js""")
  private val TOKEN_PATTERN = Regex("""eyJ[A-Za-z0-9_\-]{20,}\.[A-Za-z0-9_\-]+\.[A-Za-z0-9_\-]+""")
}
