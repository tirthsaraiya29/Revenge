package com.revenge.innertube

import io.ktor.client.plugins.ResponseException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException

/**
 * Typed failures surfaced through [Result.failure]. The hierarchy keeps transport, authentication,
 * bot-check, status, parsing and configuration failures distinguishable for callers.
 */
sealed class InnerTubeException(message: String, cause: Throwable? = null) :
  Exception(message, cause) {
  class TransportError(cause: Throwable) : InnerTubeException("Transport failure", cause)

  class AuthRequired(message: String, cause: Throwable? = null) : InnerTubeException(message, cause)

  class BotCheck(message: String, cause: Throwable? = null) : InnerTubeException(message, cause)

  class HttpError(val statusCode: Int, message: String, cause: Throwable? = null) :
    InnerTubeException(message, cause)

  class ParseError(message: String, cause: Throwable? = null) : InnerTubeException(message, cause)

  class Unavailable(val status: String, val reason: String?) :
    InnerTubeException("Playability: $status${reason?.let { " ($it)" } ?: ""}")

  class ConfigurationError(message: String) : InnerTubeException(message)

  class IllegalArgumentError(message: String) : InnerTubeException(message)
}

object InnerTubeErrors {

  /** Maps any thrown value onto the typed hierarchy. Idempotent; cancellation is never captured. */
  fun classify(throwable: Throwable): InnerTubeException {
    if (throwable is CancellationException) throw throwable
    if (throwable is InnerTubeException) return throwable
    if (throwable is ResponseException) {
      return fromHttpStatus(throwable.response.status.value, throwable.message)
    }
    if (throwable is SerializationException) {
      return InnerTubeException.ParseError(throwable.message ?: "JSON decoding failed", throwable)
    }
    if (throwable is AssertionError) {
      return InnerTubeException.IllegalArgumentError(throwable.message ?: "Assertion failed")
    }
    if (throwable is IOException) {
      return InnerTubeException.TransportError(throwable)
    }
    val kind = throwable::class.simpleName.orEmpty()
    if (
      kind.contains("Timeout", ignoreCase = true) || kind.contains("Connect", ignoreCase = true)
    ) {
      return InnerTubeException.TransportError(throwable)
    }
    return InnerTubeException.ParseError(
      throwable.message ?: kind.takeIf { it.isNotEmpty() } ?: "Unknown failure",
      throwable,
    )
  }

  fun fromHttpStatus(code: Int, message: String? = null): InnerTubeException {
    val detail = message?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
    val text = "HTTP $code$detail"
    return when (code) {
      401 -> InnerTubeException.AuthRequired(text)
      403 -> InnerTubeException.BotCheck(text)
      429 -> InnerTubeException.BotCheck(text)
      else -> InnerTubeException.HttpError(code, text)
    }
  }

  fun isRetryable(e: InnerTubeException): Boolean =
    when (e) {
      is InnerTubeException.TransportError -> true
      is InnerTubeException.HttpError -> e.statusCode in 500..599
      else -> false
    }
}
