package com.revenge.music.api

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object OpenRouterService {

    internal var clientOverride: HttpClient? = null

    private val client: HttpClient
        get() = clientOverride ?: aiHttpClient

    private const val DEFAULT_URL = "https://openrouter.ai/api/v1/chat/completions"

    fun isUrlSafe(url: String): Boolean {
        if (url.isBlank()) return true
        val uri = try {
            java.net.URI(url)
        } catch (_: Exception) {
            return false
        }
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.lowercase() ?: return false
        if (host == "169.254.169.254") return false
        if (host.startsWith("169.254.")) return false
        if (host == "metadata.google.internal") return false
        if (host == "instance-data") return false
        return true
    }

    internal fun isAnthropic(url: String): Boolean {
        val lower = url.lowercase()
        return "api.anthropic.com" in lower && "/messages" in lower
    }

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

        val expected = text.lines().size
        val system = AiPrompts.system(expected)
        val user = AiPrompts.user(mode, text, expected, targetLanguage)

        var attempt = 1
        while (attempt <= maxRetries) {
            onLog?.invoke("OpenRouter request attempt $attempt")
            try {
                val response = sendChat(
                    url = url,
                    apiKey = apiKey,
                    model = model,
                    system = system,
                    user = user,
                    maxTokens = expected * 100,
                    temperature = 0.3,
                )
                val code = response.status.value
                if (code in 500..599) {
                    delay(1_000L * attempt)
                    attempt++
                    continue
                }
                val body = response.bodyAsText()
                if (!response.status.isSuccess()) {
                    return Result.failure(
                        Exception("Translation failed: ${errorMessage(body, code)}")
                    )
                }
                val content = extractContent(body)
                if (content == null) {
                    delay(1_000L * attempt)
                    attempt++
                    continue
                }
                val lines = parseLines(content, expected)
                if (lines == null) {
                    attempt++
                    continue
                }
                onLog?.invoke("OpenRouter returned ${lines.size} line(s)")
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
            val response = sendChat(
                url = url,
                apiKey = apiKey,
                model = model,
                system = null,
                user = "ping",
                maxTokens = 10,
                temperature = null,
            )
            val code = response.status.value
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                Result.failure(Exception(errorMessage(body, code)))
            } else {
                Result.success(extractContent(body) ?: "Connection OK")
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    internal suspend fun sendChat(
        url: String,
        apiKey: String,
        model: String,
        system: String?,
        user: String,
        maxTokens: Int,
        temperature: Double?,
    ): HttpResponse {
        val anthropic = isAnthropic(url)
        val payload = buildJsonObject {
            if (model.isNotBlank()) put("model", model)
            if (anthropic) {
                if (!system.isNullOrBlank()) put("system", system)
                put(
                    "messages",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("role", "user")
                                put("content", user)
                            }
                        )
                    }
                )
            } else {
                put(
                    "messages",
                    buildJsonArray {
                        if (!system.isNullOrBlank()) {
                            add(
                                buildJsonObject {
                                    put("role", "system")
                                    put("content", system)
                                }
                            )
                        }
                        add(
                            buildJsonObject {
                                put("role", "user")
                                put("content", user)
                            }
                        )
                    }
                )
            }
            temperature?.let { put("temperature", it) }
            put("max_tokens", maxTokens)
        }.toString()

        return client.post(url) {
            contentType(ContentType.Application.Json)
            if (anthropic) {
                header("x-api-key", apiKey.trim())
                header("anthropic-version", "2023-06-01")
            } else {
                header("X-Title", "revenge")
            }
            if (apiKey.isNotBlank()) {
                header(HttpHeaders.Authorization, "Bearer ${apiKey.trim()}")
            }
            setBody(payload)
        }
    }

    private fun errorMessage(body: String, code: Int): String {
        val root = parseObject(body)
        val nested = ((root?.get("error") as? JsonObject)?.get("message") as? kotlinx.serialization.json.JsonPrimitive)
            ?.let { if (it.isString) it.content else null }
            ?.takeIf { it.isNotBlank() }
        if (nested != null) return nested
        val top = (root?.get("message") as? kotlinx.serialization.json.JsonPrimitive)
            ?.let { if (it.isString) it.content else null }
            ?.takeIf { it.isNotBlank() }
        return top ?: "HTTP $code"
    }
}
