package com.revenge.music.api

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

object MistralService {

    internal var clientOverride: HttpClient? = null

    private val client: HttpClient
        get() = clientOverride ?: aiHttpClient

    private const val ENDPOINT = "https://api.mistral.ai/v1/chat/completions"

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
        val payload = buildJsonObject {
            put("model", model.ifBlank { "mistral-small-latest" })
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", AiPrompts.user(mode, text, expected, targetLanguage))
                        }
                    )
                }
            )
            put("temperature", 0.3)
            put("max_tokens", expected * 100)
        }.toString()

        var attempt = 1
        while (attempt <= maxRetries) {
            onLog?.invoke("Mistral request attempt $attempt")
            try {
                val response = client.post(ENDPOINT) {
                    contentType(ContentType.Application.Json)
                    if (apiKey.isNotBlank()) {
                        header(HttpHeaders.Authorization, "Bearer ${apiKey.trim()}")
                    }
                    setBody(payload)
                }
                val code = response.status.value
                if (code in 500..599) {
                    delay(1_000L * attempt)
                    attempt++
                    continue
                }
                val body = response.bodyAsText()
                if (!response.status.isSuccess()) {
                    return Result.failure(
                        Exception(errorMessage(body) ?: "HTTP $code")
                    )
                }
                val content = extractContent(body)
                val lines = parseLines(content, expected)
                if (content == null || lines == null) {
                    attempt++
                    continue
                }
                onLog?.invoke("Mistral returned ${lines.size} line(s)")
                return Result.success(lines)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                if (attempt >= maxRetries) return Result.failure(error)
                delay(1_000L * attempt)
                attempt++
            }
        }
        return Result.failure(Exception("Max retries exceeded"))
    }

    private fun errorMessage(body: String): String? {
        val root = parseObject(body) as? JsonObject ?: return null
        val message = (root["error"] as? JsonObject)?.get("message")
        return (message as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }
}
