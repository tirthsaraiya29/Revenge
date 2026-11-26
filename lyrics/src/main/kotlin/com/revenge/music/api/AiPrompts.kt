package com.revenge.music.api

/**
 * Prompt construction for the chat-completion translation providers. Kept free of I/O so it can
 * be exercised directly by unit tests.
 */
internal object AiPrompts {

    fun system(lineCount: Int): String = buildString {
        append("You are a lyrics translation engine. ")
        append("Respond with only a valid JSON array of strings and nothing else. ")
        append("Do not wrap the array in prose or explanation. ")
        append("Produce exactly one output string per input line, in the same order. ")
        append("Preserve empty input lines as empty strings. ")
        append("The array must contain exactly $lineCount items. ")
        append("If you are unsure about a line, return your best approximation rather than omitting it.")
    }

    fun user(mode: String, text: String, lineCount: Int, targetLanguage: String): String =
        when (mode) {
            "Romanized" -> buildString {
                append("Transliterate every line into simple ASCII characters using only a-z, A-Z, 0-9 ")
                append("and basic punctuation. Remove all diacritics. Leave text that is already written ")
                append("in the Latin script unchanged. Do not translate the meaning; only change the script. ")
                append("Keep the line structure intact. ")
                append("Return a JSON array with exactly $lineCount string items, one per input line. ")
                append("Input lines:\n")
                append(text)
            }

            "Transcribed" -> buildString {
                append("Transcribe every line phonetically into the native script of ")
                append(targetLanguage.ifBlank { "the target language" })
                append(". Do not translate the meaning. Leave text already written in that script unchanged. ")
                append("Keep the line structure intact. ")
                append("Return a JSON array with exactly $lineCount string items, one per input line. ")
                append("Input lines:\n")
                append(text)
            }

            else -> buildString {
                append("Translate every line naturally into ")
                append(targetLanguage.ifBlank { "the target language" })
                append(". Maintain the meaning, poetic flow, punctuation and line structure. ")
                append("Return a JSON array with exactly $lineCount string items, one per input line. ")
                append("Input lines:\n")
                append(text)
            }
        }
}
