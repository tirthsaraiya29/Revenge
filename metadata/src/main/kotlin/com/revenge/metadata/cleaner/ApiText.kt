package com.revenge.metadata.cleaner

/**
 * Normalises short display strings coming back from the YouTube Music API.
 *
 * The API occasionally HTML-escapes an ampersand and then splits artist runs on the now-raw `&`,
 * so a run can arrive as an orphan fragment such as `"amp; The Pharaoh"`. Rendering the runs with
 * `", "` then produces `"Sabin Rai, amp; The Pharaoh"`. Here we decode HTML entities and drop the
 * fragments left behind by that split.
 *
 * Only the named entities that can never be legitimate standalone words (`amp;`, `quot;`,
 * `apos;`, `lt;`, `gt;`, `nbsp;`) are treated as split remnants. Numeric fragments like
 * `"#123;"` are kept because they occur naturally in ordinary text.
 */
object ApiText {

  private val NUMERIC_ENTITY = Regex("""&#(?:x([0-9a-fA-F]+)|(\d+));""")
  private val ORPHAN_FRAGMENT = Regex("""(?:^|(?<=\s))(?:amp|quot|apos|lt|gt|nbsp);\s*""")

  private val NAMED_ENTITIES =
    listOf(
      "&quot;" to "\"",
      "&apos;" to "'",
      "&lt;" to "<",
      "&gt;" to ">",
      "&nbsp;" to " ",
      "&amp;" to "&",
    )

  fun clean(raw: String): String {
    var text = NUMERIC_ENTITY.replace(raw) { match -> decode(match) }
    for ((entity, replacement) in NAMED_ENTITIES) {
      text = text.replace(entity, replacement)
    }
    return ORPHAN_FRAGMENT.replace(text, "")
  }

  private fun decode(match: MatchResult): String {
    val codePoint =
      match.groups[1]?.value?.toIntOrNull(16) ?: match.groups[2]?.value?.toIntOrNull()
    return if (codePoint != null && codePoint in 0..0x10FFFF) {
      String(Character.toChars(codePoint))
    } else {
      match.value
    }
  }
}
