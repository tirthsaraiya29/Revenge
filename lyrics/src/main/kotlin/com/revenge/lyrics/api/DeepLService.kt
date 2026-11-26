package com.revenge.lyrics.api

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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

object DeepLService {

    internal var clientOverride: HttpClient? = null

    private val client: HttpClient
        get() = clientOverride ?: aiHttpClient

    private const val FREE_ENDPOINT = "https://api-free.deepl.com/v2/translate"
    private const val PRO_ENDPOINT = "https://api.deepl.com/v2/translate"

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
        val payload = buildJsonObject {
            put("text", JsonArray(lines.map { JsonPrimitive(it) }))
            put("target_lang", deeplLanguageCode(targetLanguage))
            put("preserve_formatting", true)
            if (formality != "default") put("formality", formality)
        }.toString()

        var attempt = 1
        while (attempt <= maxRetries) {
            onLog?.invoke("DeepL request attempt $attempt")
            try {
                val response = client.post(endpoint) {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "DeepL-Auth-Key ${apiKey.trim()}")
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
                        Exception("Translation failed: ${topLevelMessage(body) ?: "HTTP $code"}")
                    )
                }
                val entries = parseTranslations(body)
                if (entries.isNullOrEmpty()) {
                    delay(1_000L * attempt)
                    attempt++
                    continue
                }
                onLog?.invoke("DeepL returned ${entries.size} line(s)")
                return Result.success(normalize(entries, expected))
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

    suspend fun testConnection(apiKey: String): Result<String> {
        if (apiKey.isBlank()) return Result.failure(Exception("DeepL API key is required"))
        return translate("Hello", "EN", apiKey).map { "DeepL connection successful!" }
    }

    internal fun deeplLanguageCode(code: String): String = when (code.lowercase()) {
        "zh", "zh-cn", "zh-hans", "zh-tw", "zh-hant" -> "ZH"
        "en", "en-us" -> "EN-US"
        "en-gb" -> "EN-GB"
        "pt", "pt-pt" -> "PT-PT"
        "pt-br" -> "PT-BR"
        else -> code.uppercase().take(2)
    }

    private fun parseTranslations(body: String): List<String>? {
        val root = parseObject(body) ?: return null
        val translations = root["translations"] as? JsonArray ?: return null
        if (translations.isEmpty()) return null
        return translations.map { element ->
            ((element as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull ?: ""
        }
    }

    private fun topLevelMessage(body: String): String? =
        (parseObject(body)?.get("message") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}
