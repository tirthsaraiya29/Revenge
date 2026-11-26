package com.revenge.music.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal val aiJson: Json = Json {
    isLenient = true
    ignoreUnknownKeys = true
}

internal fun JsonElement.stringOrNull(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonElement.lineOrEmpty(): String = (this as? JsonPrimitive)?.contentOrNull ?: ""

/**
 * Pulls the assistant text out of an OpenAI-compatible or Anthropic chat response. Anthropic
 * replies carry a `content` block array; OpenAI replies carry `choices[0].message.content`.
 */
internal fun extractContent(body: String): String? {
    val root = parseObject(body) ?: return null
    val content = root["content"]
    if (content is JsonArray) {
        val joined = content.joinToString("") { block ->
            (block as? JsonObject)?.get("text")?.stringOrNull() ?: ""
        }.trim()
        if (joined.isNotEmpty()) return joined
    }
    val choices = root["choices"] as? JsonArray ?: return null
    val message = (choices.firstOrNull() as? JsonObject)?.get("message") as? JsonObject ?: return null
    val text = message["content"]?.stringOrNull()?.trim() ?: return null
    return text.ifEmpty { null }
}

/**
 * Best-effort extraction of a list of translated lines. Handles raw JSON arrays, fenced blocks,
 * arrays embedded in prose and, as a last resort, a line-oriented split of the bracketed region.
 */
internal fun parseLines(content: String?, expected: Int): List<String>? {
    if (content == null) return null
    val trimmed = content.trim()
    var array = parseArray(trimmed)
    if (array == null) {
        val unfenced = trimmed.replace("```json", "").replace("```", "").trim()
        array = parseArray(unfenced)
    }
    val start = trimmed.indexOf('[')
    val end = trimmed.lastIndexOf(']')
    if (array == null && end > start) {
        array = parseArray(trimmed.substring(start, end + 1))
    }
    if (array != null) {
        return normalize(array.map { it.lineOrEmpty() }, expected)
    }
    if (end > start) {
        val loose = trimmed.substring(start + 1, end)
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map(::stripOneQuotePair)
        return normalize(loose, expected)
    }
    return null
}

/** Fits a translated line list to exactly [expected] entries. */
internal fun normalize(lines: List<String>, expected: Int): List<String> {
    if (expected < 0) return lines
    return when {
        lines.size == expected -> lines
        lines.size > expected -> lines.take(expected)
        else -> lines + List(expected - lines.size) { "" }
    }
}

internal fun parseObject(body: String): JsonObject? = try {
    aiJson.parseToJsonElement(body) as? JsonObject
} catch (_: Exception) {
    null
}

internal fun parseArray(content: String): JsonArray? = try {
    aiJson.parseToJsonElement(content) as? JsonArray
} catch (_: Exception) {
    null
}

private fun stripOneQuotePair(value: String): String {
    var result = value
    if (result.length >= 2 && result.first() == '"' && result.last() == '"') {
        result = result.substring(1, result.length - 1)
    }
    if (result.length >= 2 && result.first() == '\'' && result.last() == '\'') {
        result = result.substring(1, result.length - 1)
    }
    return result
}
