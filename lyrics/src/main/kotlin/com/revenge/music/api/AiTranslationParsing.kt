package com.revenge.music.api

import org.json.JSONArray

/** Shared response handling for the chat-completion based translation services. */
internal object AiTranslationParsing {
  fun normalize(lines: List<String>, expected: Int): List<String> =
    when {
      lines.size == expected -> lines
      lines.size > expected -> lines.take(expected)
      else -> lines + List(expected - lines.size) { "" }
    }

  /**
   * Extracts the assistant text from either an Anthropic Messages response (`content` blocks) or an
   * OpenAI compatible response (`choices[0].message.content`).
   */
  fun extractContent(body: String): String? {
    val json = runCatching { org.json.JSONObject(body) }.getOrNull() ?: return null
    val blocks = json.optJSONArray("content")
    if (blocks != null) {
      val text =
        (0 until blocks.length())
          .mapNotNull { blocks.optJSONObject(it)?.optString("text") }
          .joinToString("")
          .trim()
      if (text.isNotBlank()) return text
    }
    return json
      .optJSONArray("choices")
      ?.optJSONObject(0)
      ?.optJSONObject("message")
      ?.optString("content")
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
        val array = JSONArray(candidate)
        (0 until array.length()).map { array.optString(it) }
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
