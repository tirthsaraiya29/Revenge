package com.revenge.metadata.cleaner

object ApiText {

    private val DECIMAL_REF = Regex("""&#(\d+);""")
    private val HEX_REF = Regex("""&#x([0-9A-Fa-f]+);""")
    private val ORPHAN_FRAGMENT = Regex("""(^|\s)(?:amp;|quot;|apos;|lt;|gt;|nbsp;)\s*""")

    fun clean(raw: String): String {
        var text = DECIMAL_REF.replace(raw) { match ->
            decode(match.groupValues[1].toLongOrNull()) ?: match.value
        }
        text = HEX_REF.replace(text) { match ->
            decode(match.groupValues[1].toLongOrNull(16)) ?: match.value
        }

        text = text
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")

        return ORPHAN_FRAGMENT.replace(text) { match -> match.groupValues[1] }
    }

    private fun decode(value: Long?): String? {
        if (value == null || value < 0 || value > 0x10FFFF) return null
        return try {
            String(Character.toChars(value.toInt()))
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
