package com.revenge.music.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Shared response handling for the chat-completion based translation services. */
internal object AiTranslationParsing {
  private val json = Json {
    isLenient = true
    ignoreUnknownKeys = true
  }

  fun normalize(lines: List<String>, expected: Int): List<String> =
    when {
      lines.size == expected -> lines
      lines.size > expected -> lines.take(expected)
      else -> lines + List(expected - lines.size) { "" }
    }

  private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

  /**
   * Extracts the assistant text from either an Anthropic Messages response (`content` blocks) or an
   * OpenAI compatible response (`choices[0].message.content`).
   */
  fun extractContent(body: String): String? {
    val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
    val blocks = root["content"] as? JsonArray
    if (blocks != null) {
      val text =
        blocks
          .mapNotNull { (it as? JsonObject)?.string("text") }
          .joinToString("")
          .trim()
      if (text.isNotBlank()) return text
    }
    return (root["choices"] as? JsonArray)
      ?.firstOrNull()
      ?.let { it as? JsonObject }
      ?.get("message")
      ?.let { (it as? JsonObject)?.string("content") }
      ?.trim()
      ?.takeIf { it.isNotBlank() }
  }

  /**
   * Parses the model output into exactly [expected] lines. Models occasionally wrap the array in
   * prose or code fences, so several progressively looser strategies are tried.
   */
  fun parseLines(content: String, expected: Int): List<String>? {
    val trimmed = content.trim()

    fun asArray(candidate: String): List<String>? =
      runCatching {
        val array = json.parseToJsonElement(candidate) as JsonArray
        array.map { (it as? JsonPrimitive)?.contentOrNull ?: "" }
      }.getOrNull()

    val withoutFences = trimmed.replace("```json", "").replace("```", "").trim()
    val bracketStart = withoutFences.indexOf('[')
    val bracketEnd = withoutFences.lastIndexOf(']')
    val bracketed =
      if (bracketStart != -1 && bracketEnd > bracketStart) {
        withoutFences.substring(bracketStart, bracketEnd + 1)
      } else {
        null
      }

    val parsed =
      asArray(trimmed)
        ?: asArray(withoutFences)
        ?: bracketed?.let(::asArray)
        ?: bracketed?.let { candidate ->
          candidate
            .lines()
            .filter { it.trim().isNotEmpty() }
            .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
        }

    return parsed?.let { normalize(it, expected) }
  }
}
