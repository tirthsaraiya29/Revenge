package com.revenge.lyrics.api

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object OpenRouterStreamingService {

  internal var clientOverride: HttpClient? = null

  private val client: HttpClient
    get() = clientOverride ?: aiStreamingHttpClient

  private const val DEFAULT_URL = "https://openrouter.ai/api/v1/chat/completions"

  sealed class StreamChunk {
    data class Content(val text: String) : StreamChunk()

    data class Complete(val translatedLines: List<String>) : StreamChunk()

    data class Error(val message: String) : StreamChunk()
  }

  fun streamTranslation(
    text: String,
    targetLanguage: String,
    apiKey: String,
    baseUrl: String,
    model: String,
    mode: String,
  ): Flow<StreamChunk> = flow {
    if (text.isBlank()) {
      emit(StreamChunk.Error("Input text is empty"))
      return@flow
    }
    val url = baseUrl.ifBlank { DEFAULT_URL }
    if (!OpenRouterService.isUrlSafe(url)) {
      emit(StreamChunk.Error("Invalid or restricted endpoint destination"))
      return@flow
    }

    val expected = text.lines().size
    val payload = buildJsonObject {
      if (model.isNotBlank()) put("model", model)
      put(
        "messages",
        buildJsonArray {
          add(
            buildJsonObject {
              put("role", "system")
              put("content", AiPrompts.system(expected))
            },
          )
          add(
            buildJsonObject {
              put("role", "user")
              put("content", AiPrompts.user(mode, text, expected, targetLanguage))
            },
          )
        },
      )
      put("stream", true)
      put("temperature", 0.3)
      put("max_tokens", expected * 100)
    }
      .toString()

    try {
      val response =
        client.post(url) {
          contentType(ContentType.Application.Json)
          header("X-Title", "revenge")
          if (apiKey.isNotBlank()) {
            header(HttpHeaders.Authorization, "Bearer ${apiKey.trim()}")
          }
          setBody(payload)
        }

      if (!response.status.isSuccess()) {
        val body = response.bodyAsText()
        val detail = streamErrorMessage(body) ?: "HTTP ${response.status.value}"
        emit(StreamChunk.Error("Translation failed: $detail"))
        return@flow
      }

      val channel = response.bodyAsChannel()
      val accumulated = StringBuilder()
      var received = false

      while (currentCoroutineContext().isActive) {
        val line = channel.readUTF8Line() ?: break
        if (!line.startsWith("data: ")) continue
        val frame = line.removePrefix("data: ").trim()
        if (frame == "[DONE]") break
        val delta = deltaOf(frame) ?: continue
        if (delta.isEmpty()) continue
        accumulated.append(delta)
        received = true
        emit(StreamChunk.Content(delta))
      }

      currentCoroutineContext().ensureActive()
      if (received) {
        val lines = parseLines(accumulated.toString(), expected)
        if (lines == null) {
          emit(StreamChunk.Error("Failed to parse translation"))
        } else {
          emit(StreamChunk.Complete(lines))
        }
      }
    } catch (cancellation: CancellationException) {
      throw cancellation
    } catch (network: IOException) {
      currentCoroutineContext().ensureActive()
      emit(StreamChunk.Error(network.message ?: "Network error"))
    } catch (error: Exception) {
      currentCoroutineContext().ensureActive()
      emit(StreamChunk.Error(error.message ?: "Unknown error"))
    }
  }

  private fun deltaOf(frame: String): String? {
    val root =
      try {
        parseObject(frame)
      } catch (_: Exception) {
        null
      } ?: return null
    val choices = root["choices"] as? JsonArray ?: return null
    val first = choices.firstOrNull() as? JsonObject ?: return null
    val delta = first["delta"] as? JsonObject ?: return null
    return (delta["content"] as? kotlinx.serialization.json.JsonPrimitive)?.let {
      if (it.isString) it.content else null
    }
  }

  private fun streamErrorMessage(body: String): String? {
    val root = parseObject(body) ?: return null
    val message =
      (root["error"] as? JsonObject)?.get("message") as? kotlinx.serialization.json.JsonPrimitive
    return message?.let { if (it.isString) it.content else null }?.takeIf { it.isNotBlank() }
  }
}
