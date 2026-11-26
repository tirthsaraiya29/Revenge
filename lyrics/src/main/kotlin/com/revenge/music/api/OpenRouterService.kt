package com.revenge.music.api

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

object OpenRouterService {
  private const val DEFAULT_URL = "https://openrouter.ai/api/v1/chat/completions"
  private const val ANTHROPIC_VERSION = "2023-06-01"

  fun isUrlSafe(url: String): Boolean {
    if (url.isBlank()) return true
    val uri = runCatching { java.net.URI(url.trim()) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase() ?: return false
    if (scheme != "https" && scheme != "http") return false
    val host = uri.host?.lowercase() ?: return false
    return !(
      host == "169.254.169.254" ||
        host.startsWith("169.254.") ||
        host == "metadata.google.internal" ||
        host == "instance-data"
    )
  }

  private fun isAnthropic(url: String): Boolean =
    url.contains("api.anthropic.com", ignoreCase = true) &&
      url.contains("/messages", ignoreCase = true)

  private suspend fun requestChat(
    url: String,
    apiKey: String,
    model: String,
    system: String?,
    user: String,
    maxTokens: Int,
    temperature: Double?,
  ): ChatHttpResponse {
    val anthropic = isAnthropic(url)
    val payload =
      JSONObject().apply {
        if (model.isNotBlank()) put("model", model)
        if (anthropic) {
          if (!system.isNullOrBlank()) put("system", system)
          put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", user)))
        } else {
          val messages = JSONArray()
          if (!system.isNullOrBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", system))
          }
          messages.put(JSONObject().put("role", "user").put("content", user))
          put("messages", messages)
        }
        if (temperature != null) put("temperature", temperature)
        put("max_tokens", maxTokens)
      }

    val response =
      aiHttpClient.post(url) {
        if (apiKey.isNotBlank()) {
          if (anthropic) header("x-api-key", apiKey.trim())
          else header("Authorization", "Bearer ${apiKey.trim()}")
        }
        if (anthropic) header("anthropic-version", ANTHROPIC_VERSION) else header("X-Title", "revenge")
        contentType(ContentType.Application.Json)
        setBody(payload.toString())
      }
    return ChatHttpResponse(response.status.isSuccess(), response.status.value, response.bodyAsText())
  }

  private fun errorMessage(body: String, code: Int): String =
    runCatching {
      val json = JSONObject(body)
      json.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
        ?: json.optString("message").takeIf { it.isNotBlank() }
    }.getOrNull() ?: "HTTP $code"

  suspend fun translate(
    text: String,
    targetLanguage: String,
    apiKey: String,
    baseUrl: String,
    model: String,
    mode: String,
    maxRetries: Int = 3,
    sourceLanguage: String? = null,
    onLog: ((String) -> Unit)? = null,
  ): Result<List<String>> {
    val url = baseUrl.ifBlank { DEFAULT_URL }
    if (!isUrlSafe(url)) {
      return Result.failure(SecurityException("Invalid or restricted endpoint destination"))
    }
    if (text.isBlank()) return Result.failure(Exception("Input text is empty"))

    val lines = text.lines()
    val expected = lines.size
    val userPrompt = AiPrompts.user(mode, text, expected, targetLanguage)

    onLog?.invoke("Starting translation...")
    var attempt = 0
    while (attempt < maxRetries) {
      attempt++
      try {
        onLog?.invoke("Calling OpenRouter API (Attempt $attempt)...")
        val response =
          requestChat(
            url = url,
            apiKey = apiKey,
            model = model,
            system = AiPrompts.system(expected),
            user = userPrompt,
            maxTokens = expected * 100,
            temperature = 0.3,
          )

        if (!response.success) {
          if (response.code >= 500) {
            delay(1000L * attempt)
            continue
          }
          return Result.failure(
            Exception("Translation failed: ${errorMessage(response.body, response.code)}"),
          )
        }

        val content = AiTranslationParsing.extractContent(response.body) ?: run {
          delay(1000L * attempt)
          continue
        }
        val parsed = AiTranslationParsing.parseLines(content, expected)
        if (parsed != null) {
          onLog?.invoke("Successfully parsed ${parsed.size} lines")
          return Result.success(parsed)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        if (attempt >= maxRetries) return Result.failure(e)
        delay(1000L * attempt)
      }
    }
    return Result.failure(Exception("Max retries exceeded"))
  }

  suspend fun testConnection(
    baseUrl: String,
    apiKey: String,
    model: String,
  ): Result<String> {
    val url = baseUrl.ifBlank { DEFAULT_URL }
    if (!isUrlSafe(url)) {
      return Result.failure(SecurityException("Invalid or restricted endpoint destination"))
    }
    return try {
      val response =
        requestChat(
          url = url,
          apiKey = apiKey,
          model = model,
          system = null,
          user = "ping",
          maxTokens = 10,
          temperature = null,
        )
      if (!response.success) {
        Result.failure(Exception(errorMessage(response.body, response.code)))
      } else {
        Result.success(AiTranslationParsing.extractContent(response.body) ?: "Connection OK")
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  private data class ChatHttpResponse(val success: Boolean, val code: Int, val body: String)
}
