package com.revenge.music.api

import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readLine
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import org.json.JSONArray
import org.json.JSONObject

object OpenRouterStreamingService {
  private const val DEFAULT_URL = "https://openrouter.ai/api/v1/chat/completions"

  /**
   * Streams a translation of [text]. Emits each raw content delta, then either a [StreamChunk.Complete]
   * with the parsed lines or a single [StreamChunk.Error]. Cancelling the collector cancels the
   * underlying HTTP call.
   */
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
    val payload =
      JSONObject().apply {
        if (model.isNotBlank()) put("model", model)
        put(
          "messages",
          JSONArray().apply {
            put(
              JSONObject()
                .put("role", "system")
                .put("content", AiPrompts.system(expected)),
            )
            put(
              JSONObject()
                .put("role", "user")
                .put("content", AiPrompts.user(mode, text, expected, targetLanguage)),
            )
          },
        )
        put("stream", true)
        put("temperature", 0.3)
        put("max_tokens", expected * 100)
      }

    try {
      val statement =
        aiStreamingHttpClient.preparePost(url) {
          if (apiKey.isNotBlank()) header("Authorization", "Bearer ${apiKey.trim()}")
          header("X-Title", "revenge")
          contentType(ContentType.Application.Json)
          setBody(payload.toString())
        }

      statement.execute { response ->
        if (!response.status.isSuccess()) {
          val body = response.bodyAsText()
          val message =
            runCatching {
              JSONObject(body).optJSONObject("error")?.optString("message")
            }.getOrNull() ?: "HTTP ${response.status.value}"
          emit(StreamChunk.Error("Translation failed: $message"))
          return@execute
        }

        val channel = response.bodyAsChannel()
        val content = StringBuilder()

        while (currentCoroutineContext().isActive) {
          val line = channel.readLine() ?: break
          if (!line.startsWith("data: ")) continue

          val data = line.removePrefix("data: ").trim()
          if (data == "[DONE]") break

          val delta =
            runCatching {
              JSONObject(data)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("delta")
                ?.optString("content")
            }.getOrNull()
          if (!delta.isNullOrEmpty()) {
            content.append(delta)
            emit(StreamChunk.Content(delta))
          }
        }

        currentCoroutineContext().ensureActive()
        if (content.isNotEmpty()) {
          val parsed = AiTranslationParsing.parseLines(content.toString(), expected)
          if (parsed != null) {
            emit(StreamChunk.Complete(parsed))
          } else {
            emit(StreamChunk.Error("Failed to parse translation"))
          }
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: IOException) {
      currentCoroutineContext().ensureActive()
      emit(StreamChunk.Error(e.message ?: "Network error"))
    } catch (e: Exception) {
      currentCoroutineContext().ensureActive()
      emit(StreamChunk.Error(e.message ?: "Unknown error"))
    }
  }.flowOn(Dispatchers.IO)

  sealed class StreamChunk {
    data class Content(val text: String) : StreamChunk()

    data class Complete(val translatedLines: List<String>) : StreamChunk()

    data class Error(val message: String) : StreamChunk()
  }
}
