package com.revenge.shazamkit

import com.revenge.shazamkit.models.RecognitionResult
import com.revenge.shazamkit.models.ShazamRequestJson
import com.revenge.shazamkit.models.ShazamResponseJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import java.io.IOException
import java.util.Collections
import java.util.LinkedHashMap
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Shazam music recognition.
 *
 * Requests are rate limited (at most one start per second), executed with a bounded concurrency of
 * [MAX_CONCURRENT_REQUESTS], retried on transient failures and cached for [CACHE_DURATION_MS]. All
 * public calls are safe to invoke from any coroutine context.
 */
object Shazam {

  private const val ENDPOINT = "https://amp.shazam.com/discovery/v5/en/US/android/-/tag"
  private const val MAX_CONCURRENT_REQUESTS = 2
  private const val MIN_REQUEST_INTERVAL_MS = 1_000L
  private const val MAX_RETRIES = 3
  private const val INITIAL_RETRY_DELAY_MS = 2_000L
  private const val CACHE_DURATION_MS = 300_000L
  private const val MAX_CACHE_ENTRIES = 100
  private const val MAX_QUEUE_SIZE = 50
  private const val REQUEST_TIMEOUT_MS = 20_000L
  private const val CONNECT_TIMEOUT_MS = 10_000L
  private const val WAITER_POLL_INTERVAL_MS = 50L

  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
  }

  private val client by lazy {
    HttpClient(OkHttp) {
      expectSuccess = false
      install(ContentNegotiation) { json(json) }
      install(HttpTimeout) {
        requestTimeoutMillis = REQUEST_TIMEOUT_MS
        connectTimeoutMillis = CONNECT_TIMEOUT_MS
        socketTimeoutMillis = REQUEST_TIMEOUT_MS
      }
    }
  }

  private val permits = Semaphore(MAX_CONCURRENT_REQUESTS)

  // Guards rate-limit bookkeeping so concurrent callers cannot start together.
  private val rateLimitMutex = Mutex()
  private var lastRequestStartedAt = 0L

  private val inFlight = AtomicInteger(0)
  private val active = AtomicInteger(0)
  private val cancelEpoch = AtomicLong(0)

  // Access-ordered map doubles as a bounded LRU; operations are individually synchronized.
  private val cache =
    Collections.synchronizedMap(
      object : LinkedHashMap<String, CachedResult>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedResult>) =
          size > MAX_CACHE_ENTRIES
      },
    )

  private val userAgents =
    listOf(
      "Dalvik/2.1.0 (Linux; U; Android 5.0.2; VS980 4G Build/LRX22G)",
      "Dalvik/1.6.0 (Linux; U; Android 4.4.2; SM-T210 Build/KOT49H)",
      "Dalvik/2.1.0 (Linux; U; Android 5.1.1; SM-P905V Build/LMY47X)",
      "Dalvik/2.1.0 (Linux; U; Android 6.0.1; SM-G920F Build/MMB29K)",
      "Dalvik/2.1.0 (Linux; U; Android 5.0; SM-G900F Build/LRX21T)",
    )

  private val timezones =
    listOf(
      "Europe/Paris",
      "Europe/London",
      "America/New_York",
      "America/Los_Angeles",
      "Asia/Tokyo",
      "Asia/Dubai",
    )

  /**
   * Recognize the track behind an audio signature.
   *
   * @param signature audio fingerprint in Shazam DejaVu format.
   * @param sampleDurationMs duration of the sampled audio in milliseconds.
   * @return success with a [RecognitionResult], or failure with a readable message. A missing match
   *   surfaces as a failure whose message contains "No match".
   */
  suspend fun recognize(signature: String, sampleDurationMs: Long): Result<RecognitionResult> {
    if (signature.isBlank()) {
      return Result.failure(IllegalArgumentException("Audio signature is empty"))
    }
    if (sampleDurationMs <= 0) {
      return Result.failure(IllegalArgumentException("Sample duration must be positive"))
    }

    cachedResult(signature)?.let {
      return Result.success(it)
    }

    if (inFlight.incrementAndGet() > MAX_QUEUE_SIZE) {
      inFlight.decrementAndGet()
      return Result.failure(IllegalStateException("Too many pending recognition requests"))
    }

    val epoch = cancelEpoch.get()
    try {
      acquirePermit(epoch)
      active.incrementAndGet()
      try {
        val result = withRetry(epoch) { executeRequest(signature, sampleDurationMs) }
        storeInCache(signature, result)
        return Result.success(result)
      } finally {
        active.decrementAndGet()
        permits.release()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      return Result.failure(e)
    } finally {
      inFlight.decrementAndGet()
    }
  }

  /** Number of requests currently waiting for an execution slot. */
  fun getPendingRequestsCount(): Int = (inFlight.get() - active.get()).coerceAtLeast(0)

  /** Number of requests performing an HTTP call right now. */
  fun getActiveRequestsCount(): Int = active.get()

  /** Drop every cached recognition result. */
  fun clearCache() {
    cache.clear()
  }

  /**
   * Cancel requests that are still waiting for an execution slot. Requests already talking to
   * Shazam are allowed to finish.
   */
  fun cancelPendingRequests() {
    cancelEpoch.incrementAndGet()
  }

  /** Release HTTP resources. The instance is not usable for recognition afterwards. */
  fun cleanup() {
    cancelPendingRequests()
    clearCache()
    client.close()
  }

  private suspend fun acquirePermit(epoch: Long) {
    while (!permits.tryAcquire()) {
      if (cancelEpoch.get() != epoch) {
        throw CancellationException("Recognition request cancelled")
      }
      delay(WAITER_POLL_INTERVAL_MS)
    }
    if (cancelEpoch.get() != epoch) {
      permits.release()
      throw CancellationException("Recognition request cancelled")
    }
  }

  private suspend fun withRetry(
    epoch: Long,
    block: suspend () -> RecognitionResult,
  ): RecognitionResult {
    var attempt = 0
    while (true) {
      if (cancelEpoch.get() != epoch) {
        throw CancellationException("Recognition request cancelled")
      }
      try {
        awaitRateLimitSlot()
        return block()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        if (attempt >= MAX_RETRIES - 1 || !e.isRetryable()) throw e
        delay(INITIAL_RETRY_DELAY_MS shl attempt)
        attempt++
      }
    }
  }

  private suspend fun awaitRateLimitSlot() {
    rateLimitMutex.withLock {
      val now = System.currentTimeMillis()
      val remaining = MIN_REQUEST_INTERVAL_MS - (now - lastRequestStartedAt)
      if (remaining > 0) delay(remaining)
      lastRequestStartedAt = System.currentTimeMillis()
    }
  }

  private suspend fun executeRequest(
    signature: String,
    sampleDurationMs: Long,
  ): RecognitionResult {
    val timestampSeconds = System.currentTimeMillis() / 1000
    val sessionId = UUID.randomUUID().toString().uppercase()
    val requestId = UUID.randomUUID().toString()

    val payload =
      ShazamRequestJson(
        geolocation =
          ShazamRequestJson.Geolocation(
            altitude = Random.nextDouble() * 400 + 100,
            latitude = Random.nextDouble() * 180 - 90,
            longitude = Random.nextDouble() * 360 - 180,
          ),
        signature =
          ShazamRequestJson.Signature(
            samplems = sampleDurationMs,
            timestamp = timestampSeconds,
            uri = signature,
          ),
        timestamp = timestampSeconds,
        timezone = timezones.random(),
      )

    val response =
      client.post("$ENDPOINT/$sessionId/$requestId") {
        parameter("sync", "true")
        parameter("webv3", "true")
        parameter("sampling", "true")
        parameter("connected", "")
        parameter("shazamapiversion", "v3")
        parameter("sharehub", "true")
        parameter("video", "v3")
        header("User-Agent", userAgents.random())
        header("Content-Language", "en_US")
        contentType(ContentType.Application.Json)
        setBody(payload)
      }

    if (!response.status.isSuccess()) {
      throw httpErrorFor(response.status.value)
        ?: ShazamHttpException(response.status.value, "Recognition failed")
    }

    val body =
      try {
        response.body<ShazamResponseJson>()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        throw ShazamHttpException(response.status.value, "Malformed response from Shazam", e)
      }

    return body.toRecognitionResult() ?: throw ShazamHttpException(404, "No match found")
  }

  private fun cachedResult(signature: String): RecognitionResult? {
    val entry = cache[signature] ?: return null
    if (System.currentTimeMillis() - entry.storedAt > CACHE_DURATION_MS) {
      cache.remove(signature)
      return null
    }
    return entry.result
  }

  private fun storeInCache(signature: String, result: RecognitionResult) {
    cache[signature] = CachedResult(System.currentTimeMillis(), result)
  }

  private fun Exception.isRetryable(): Boolean =
    this is IOException || (this is ShazamHttpException && (code == 429 || code in 500..599))

  private data class CachedResult(val storedAt: Long, val result: RecognitionResult)
}

/** Non-2xx response from the Shazam endpoint. */
internal class ShazamHttpException(
  val code: Int,
  message: String,
  cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Maps an HTTP status to the exception surfaced to callers, or null when the status is a success
 * code that needs no special handling.
 */
internal fun httpErrorFor(statusCode: Int): ShazamHttpException? =
  when {
    statusCode in 200..299 -> null
    statusCode == 404 -> ShazamHttpException(404, "No match found")
    statusCode == 429 -> ShazamHttpException(429, "Too many requests")
    statusCode in 500..599 ->
      ShazamHttpException(statusCode, "Shazam service temporarily unavailable")
    else -> ShazamHttpException(statusCode, "Recognition failed (error $statusCode)")
  }

/** Flattens a discovery response into the app-facing model, or null when no track matched. */
internal fun ShazamResponseJson.toRecognitionResult(): RecognitionResult? {
  val track = track ?: return null
  val sections = track.sections.orEmpty().filterNotNull()
  val metadata = sections.firstOrNull { it.type == "SONG" }?.metadata.orEmpty().filterNotNull()

  fun metadataValue(name: String): String? =
    metadata.firstOrNull { it.title == name }?.text?.takeIf { it.isNotBlank() }

  val appleAction =
    track.hub
      ?.options
      .orEmpty()
      .filterNotNull()
      .firstOrNull { it.providername?.contains("apple", ignoreCase = true) == true }
      ?.actions
      .orEmpty()
      .filterNotNull()
      .firstOrNull()

  val spotifyAction =
    track.hub
      ?.providers
      .orEmpty()
      .filterNotNull()
      .firstOrNull { it.caption?.contains("spotify", ignoreCase = true) == true }
      ?.actions
      .orEmpty()
      .filterNotNull()
      .firstOrNull()

  val youtubeUri =
    track.hub
      ?.options
      .orEmpty()
      .filterNotNull()
      .firstOrNull { it.type?.contains("video", ignoreCase = true) == true }
      ?.actions
      .orEmpty()
      .filterNotNull()
      .firstOrNull()
      ?.uri

  return RecognitionResult(
    trackId = track.key?.takeIf { it.isNotBlank() } ?: tagid.orEmpty(),
    title = track.title.orEmpty(),
    artist = track.subtitle.orEmpty(),
    album = metadataValue("Album"),
    coverArtUrl = track.images?.coverart,
    coverArtHqUrl = track.images?.coverarthq,
    genre = track.genres?.primary?.takeIf { it.isNotBlank() },
    releaseDate = metadataValue("Released"),
    label = metadataValue("Label"),
    lyrics = sections.firstOrNull { it.type == "LYRICS" }?.text?.takeIf { it.isNotEmpty() },
    shazamUrl = track.url,
    appleMusicUrl = appleAction?.uri,
    spotifyUrl = spotifyAction?.uri,
    isrc = track.isrc?.takeIf { it.isNotBlank() },
    youtubeVideoId = extractYouTubeVideoId(youtubeUri),
  )
}

/** Extracts an 11-character YouTube video id from a watch, short or embed URL. */
internal fun extractYouTubeVideoId(uri: String?): String? {
  val value = uri?.trim().orEmpty()
  if (value.isEmpty()) return null

  val queryValue = value.substringAfter("v=", "")
  if (queryValue.isNotEmpty()) {
    val candidate = queryValue.substringBefore('&').substringBefore('?')
    if (candidate.isValidYouTubeId()) return candidate
  }

  val pathCandidate = value.substringAfterLast('/').substringBefore('?').substringBefore('&')
  return pathCandidate.takeIf { it.isValidYouTubeId() }
}

private fun String.isValidYouTubeId(): Boolean =
  length == 11 && all { it.isLetterOrDigit() || it == '_' || it == '-' }
