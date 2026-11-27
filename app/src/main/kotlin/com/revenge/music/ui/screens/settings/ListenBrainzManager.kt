package com.revenge.music.ui.screens.settings

import android.content.Context
import com.revenge.music.BuildConfig
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Fire-and-forget ListenBrainz submissions. */
object ListenBrainzManager {

  private const val ENDPOINT = "https://api.listenbrainz.org/1/submit-listens"
  private const val MIN_LISTENED_AT = 1_033_430_400L
  private const val CLIENT = "Revenge"

  private val _lastSubmitTimeFlow = MutableStateFlow<Long?>(null)
  val lastSubmitTimeFlow: StateFlow<Long?> = _lastSubmitTimeFlow.asStateFlow()

  private val active = AtomicInteger(0)

  internal var nowSeconds: () -> Long = { System.currentTimeMillis() / 1000L }

  internal var httpPost: suspend (token: String, body: String) -> Boolean = { token, body ->
    post(token, body)
  }

  fun isRunning(): Boolean = active.get() > 0

  suspend fun submitPlayingNow(
    context: Context,
    token: String,
    title: String,
    artistNames: String,
    releaseName: String,
    durationMs: Long,
    positionMs: Long,
  ): Boolean =
    submit(
      token = token,
      title = title,
      body =
        buildString {
          append("{\"listen_type\":\"playing_now\",\"payload\":[{\"track_metadata\":{")
          appendMetadata(artistNames, title, releaseName)
          append(",\"additional_info\":{\"duration_ms\":")
          append(durationMs)
          append(",\"position_ms\":")
          append(positionMs)
          appendClientInfo()
          append("}}}]}")
        },
    )

  suspend fun submitFinished(
    context: Context,
    token: String,
    title: String,
    artistNames: String,
    releaseName: String,
    durationMs: Long,
    startMs: Long,
    endMs: Long,
  ): Boolean {
    val listenedAt = (startMs / 1000L).let { if (it < MIN_LISTENED_AT) nowSeconds() else it }
    return submit(
      token = token,
      title = title,
      body =
        buildString {
          append("{\"listen_type\":\"single\",\"payload\":[{\"listened_at\":")
          append(listenedAt)
          append(",\"track_metadata\":{")
          appendMetadata(artistNames, title, releaseName)
          append(",\"additional_info\":{\"duration_ms\":")
          append(durationMs)
          append(",\"start_ms\":")
          append(startMs)
          append(",\"end_ms\":")
          append(endMs)
          appendClientInfo()
          append("}}}]}")
        },
    )
  }

  private suspend fun submit(token: String, title: String, body: String): Boolean {
    if (token.isBlank() || title.isBlank()) return false
    active.incrementAndGet()
    return try {
      httpPost(token, body).also { success ->
        if (success) _lastSubmitTimeFlow.value = System.currentTimeMillis()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      Timber.tag("ListenBrainz").w(e, "Submission failed")
      false
    } finally {
      active.decrementAndGet()
    }
  }

  private fun StringBuilder.appendMetadata(
    artistNames: String,
    title: String,
    releaseName: String,
  ) {
    append("\"artist_name\":\"")
    append(escape(artistNames))
    append("\",\"track_name\":\"")
    append(escape(title))
    append('"')
    if (releaseName.isNotBlank()) {
      append(",\"release_name\":\"")
      append(escape(releaseName))
      append('"')
    }
  }

  private fun StringBuilder.appendClientInfo() {
    append(",\"submission_client\":\"")
    append(CLIENT)
    append("\",\"submission_client_version\":\"")
    append(escape(BuildConfig.VERSION_NAME))
    append('"')
  }

  internal fun escape(value: String): String =
    buildString(value.length + 8) {
      value.forEach { character ->
        when (character) {
          '\\' -> append("\\\\")
          '"' -> append("\\\"")
          '\n' -> append("\\n")
          else -> append(character)
        }
      }
    }

  private suspend fun post(token: String, body: String): Boolean =
    withContext(Dispatchers.IO) {
      val connection =
        (URI(ENDPOINT).toURL().openConnection() as HttpURLConnection).apply {
          requestMethod = "POST"
          connectTimeout = 15_000
          readTimeout = 20_000
          doOutput = true
          useCaches = false
          setRequestProperty("Content-Type", "application/json")
          setRequestProperty("Authorization", "Token $token")
          setRequestProperty("User-Agent", "Revenge/${BuildConfig.VERSION_NAME}")
        }
      try {
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        connection.responseCode in 200..299
      } finally {
        connection.disconnect()
      }
    }
}
