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

object DeepLService {
  private const val FREE_ENDPOINT = "https://api-free.deepl.com/v2/translate"
  private const val PRO_ENDPOINT = "https://api.deepl.com/v2/translate"

  private fun deeplLanguageCode(language: String): String =
    when (language.lowercase()) {
      "zh",
      "zh-cn",
      "zh-hans",
      "zh-tw",
      "zh-hant" -> "ZH"
      "en",
      "en-us" -> "EN-US"
      "en-gb" -> "EN-GB"
      "pt",
      "pt-pt" -> "PT-PT"
      "pt-br" -> "PT-BR"
      else -> language.uppercase().take(2)
    }

  /**
   * Translates [text] line by line. The returned list always has exactly one entry per input line;
   * missing translations are padded with empty strings and surplus ones are dropped.
   */
  suspend fun translate(
    text: String,
    targetLanguage: String,
    apiKey: String,
    formality: String = "default",
    maxRetries: Int = 3,
    onLog: ((String) -> Unit)? = null,
  ): Result<List<String>> {
    if (text.isBlank()) return Result.failure(Exception("Input text is empty"))

    val lines = text.lines()
    val expected = lines.size
    val endpoint = if (apiKey.endsWith(":fx")) FREE_ENDPOINT else PRO_ENDPOINT
    val payload =
      JSONObject().apply {
        put("text", JSONArray().apply { lines.forEach { put(it) } })
        put("target_lang", deeplLanguageCode(targetLanguage))
        if (formality != "default") put("formality", formality)
        put("preserve_formatting", true)
      }

    onLog?.invoke("Starting DeepL translation...")
    var attempt = 0
    while (attempt < maxRetries) {
      attempt++
      try {
        onLog?.invoke("Calling DeepL API (Attempt $attempt)...")
        val response =
          aiHttpClient.post(endpoint) {
            header("Authorization", "DeepL-Auth-Key ${apiKey.trim()}")
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
          }
        val body = response.bodyAsText()

        if (!response.status.isSuccess()) {
          if (response.status.value >= 500) {
            delay(1000L * attempt)
            continue
          }
          return Result.failure(Exception("Translation failed: ${deeplError(body, response.status.value)}"))
        }

        val translations = JSONObject(body).optJSONArray("translations")
        if (translations == null || translations.length() == 0) {
          delay(1000L * attempt)
          continue
        }

        val translated =
          (0 until translations.length()).map { index ->
            translations.optJSONObject(index)?.optString("text", "").orEmpty()
          }
        onLog?.invoke("Successfully parsed ${translated.size} lines")
        return Result.success(normalize(translated, expected))
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        if (attempt >= maxRetries) return Result.failure(e)
        delay(1000L * attempt)
      }
    }
    return Result.failure(Exception("Max retries exceeded"))
  }

  suspend fun testConnection(apiKey: String): Result<String> {
    if (apiKey.isBlank()) return Result.failure(Exception("DeepL API key is required"))
    return translate(text = "Hello", targetLanguage = "EN", apiKey = apiKey).map {
      "DeepL connection successful!"
    }
  }

  private fun deeplError(body: String, code: Int): String =
    runCatching { JSONObject(body).optString("message").takeIf { it.isNotBlank() } }.getOrNull()
      ?: "HTTP $code"

  private fun normalize(lines: List<String>, expected: Int): List<String> =
    when {
      lines.size == expected -> lines
      lines.size > expected -> lines.take(expected)
      else -> lines + List(expected - lines.size) { "" }
    }
}
