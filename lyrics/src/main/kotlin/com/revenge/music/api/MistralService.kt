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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

private val mistralJson = Json {
  isLenient = true
  ignoreUnknownKeys = true
}

private fun JsonObject.string(key: String): String? =
  (this[key] as? JsonPrimitive)?.contentOrNull

object MistralService {
  private const val ENDPOINT = "https://api.mistral.ai/v1/chat/completions"

  private fun errorMessage(body: String, code: Int): String =
    runCatching {
        ((mistralJson.parseToJsonElement(body) as? JsonObject)?.get("error") as? JsonObject)
          ?.string("message")
          ?.takeIf { it.isNotBlank() }
      }
      .getOrNull() ?: "HTTP $code"

  suspend fun translate(
    text: String,
    targetLanguage: String,
    apiKey: String,
    model: String,
    mode: String,
    maxRetries: Int = 3,
    sourceLanguage: String? = null,
    onLog: ((String) -> Unit)? = null,
  ): Result<List<String>> {
    if (text.isBlank()) return Result.failure(Exception("Input text is empty"))

    val expected = text.lines().size
    val payload =
      buildJsonObject {
        put("model", model.ifBlank { "mistral-small-latest" })
        put(
          "messages",
          buildJsonArray {
            add(
              buildJsonObject {
                put("role", "user")
                put("content", AiPrompts.user(mode, text, expected, targetLanguage))
              },
            )
          },
        )
        put("temperature", 0.3)
        put("max_tokens", expected * 100)
      }

    onLog?.invoke("Starting translation...")
    var attempt = 0
    while (attempt < maxRetries) {
      attempt++
      try {
        onLog?.invoke("Calling Mistral API (Attempt $attempt)...")
        val response =
          aiHttpClient.post(ENDPOINT) {
            if (apiKey.isNotBlank()) header("Authorization", "Bearer ${apiKey.trim()}")
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
          }
        val body = response.bodyAsText()

        if (!response.status.isSuccess()) {
          if (response.status.value >= 500) {
            delay(1000L * attempt)
            continue
          }
          return Result.failure(
            Exception("Translation failed: ${errorMessage(body, response.status.value)}"),
          )
        }

        val content = AiTranslationParsing.extractContent(body)
        if (content != null) {
          val parsed = AiTranslationParsing.parseLines(content, expected)
          if (parsed != null) {
            onLog?.invoke("Successfully parsed ${parsed.size} lines")
            return Result.success(parsed)
          }
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
}
