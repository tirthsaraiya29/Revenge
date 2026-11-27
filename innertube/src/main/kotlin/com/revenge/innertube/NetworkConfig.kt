package com.revenge.innertube

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import java.io.File
import kotlinx.serialization.json.Json
import okhttp3.Cache

enum class NetworkQuality {
  EXCELLENT,
  GOOD,
  POOR,
  UNKNOWN,
}

data class TimeoutConfig(
  val connectTimeout: Long,
  val readTimeout: Long,
  val requestTimeout: Long,
)

/**
 * Stateless HTTP-client factory kept for source compatibility. The InnerTube transport builds its
 * own client (it has to follow mutable proxy configuration); this object only exposes the shared
 * JSON/timeout/cache defaults.
 */
object NetworkConfig {

  private const val CACHE_BYTES = 50L * 1024L * 1024L

  private fun cacheDirFor(cacheDir: File?): File =
    cacheDir ?: File(System.getProperty("java.io.tmpdir"), "revenge_http_cache")

  fun createOptimizedHttpClient(cacheDir: File? = null, enableCache: Boolean = true): HttpClient =
    HttpClient(OkHttp) {
      expectSuccess = true
      install(ContentNegotiation) {
        json(
          Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
            isLenient = true
          },
        )
      }
      install(HttpTimeout) {
        requestTimeoutMillis = 60_000
        connectTimeoutMillis = 30_000
        socketTimeoutMillis = 60_000
      }
      engine {
        config {
          if (enableCache) {
            cache(Cache(cacheDirFor(cacheDir), CACHE_BYTES))
          }
        }
      }
    }

  fun createYouTubeMusicClient(cacheDir: File? = null): HttpClient =
    createOptimizedHttpClient(cacheDir)

  fun getAdaptiveTimeouts(networkQuality: NetworkQuality): TimeoutConfig =
    when (networkQuality) {
      NetworkQuality.EXCELLENT -> TimeoutConfig(10_000L, 30_000L, 45_000L)
      NetworkQuality.GOOD -> TimeoutConfig(20_000L, 45_000L, 60_000L)
      NetworkQuality.POOR -> TimeoutConfig(30_000L, 60_000L, 90_000L)
      NetworkQuality.UNKNOWN -> TimeoutConfig(30_000L, 60_000L, 60_000L)
    }
}

/** Decoder/encoder settings shared by the transport and its callers. */
internal val innertubeJson: Json = Json {
  ignoreUnknownKeys = true
  explicitNulls = false
  encodeDefaults = true
}
