package com.revenge.music.api

/** Chat prompts shared by every LLM-backed translation provider. */
internal object AiPrompts {
  fun system(lineCount: Int): String =
    """You are a precise lyrics translation assistant. Your output must ALWAYS be a valid JSON array of strings.

CRITICAL RULES:
1. Output ONLY a JSON array: ["line1", "line2", "line3"]
2. NO explanations, NO questions, NO additional text
3. Each input line maps to exactly one output line
4. Preserve empty lines as empty strings ""
5. Return EXACTLY $lineCount items in the array
6. If uncertain, provide best approximation but maintain line count"""

  fun user(
    mode: String,
    text: String,
    lineCount: Int,
    targetLanguage: String,
  ): String =
    when (mode) {
      "Romanized" ->
        """Romanize/transliterate the following $lineCount lines into simple Latin script using ONLY basic English letters (a-z, A-Z).

CRITICAL REQUIREMENTS:
- Use ONLY simple ASCII characters (a-z, A-Z, 0-9, basic punctuation)
- NO special characters like ā, ī, ū, ñ, ç, etc.
- NO diacritics or accent marks
- If text is already in Latin script, return it UNCHANGED
- For non-Latin scripts (Hindi, Chinese, Japanese, Korean, Cyrillic, etc.), provide simple romanization
- DO NOT translate meaning, only convert script to simple English letters
- Keep all punctuation and formatting
- Preserve line-by-line structure exactly

Examples of correct simple romanization:
- Sanskrit/Hindi "आ" → "aa" (not "ā")
- Japanese "東京" → "toukyou" or "tokyo" (not "tōkyō")
- Korean "서울" → "seoul" (not "sŏul")

Input ($lineCount lines):
$text

Output MUST be a JSON array with EXACTLY $lineCount strings using ONLY simple ASCII characters."""
      "Transcribed" ->
        """Transcribe/transliterate the following $lineCount lines phonetically into $targetLanguage script.

CRITICAL REQUIREMENTS:
- Convert the SOUND/PRONUNCIATION of the original text into $targetLanguage script
- DO NOT translate the meaning - only represent how the original words SOUND
- Use the native script of $targetLanguage (e.g., Devanagari for Hindi, Hangul for Korean, etc.)
- Preserve the original pronunciation as closely as possible in the target script
- Keep punctuation and formatting
- Preserve line-by-line structure exactly
- If text is already in $targetLanguage script, return it UNCHANGED

Examples:
- Japanese "こんにちは" to Hindi → "कोन्निचिवा" (phonetic, not translation)
- English "Hello" to Hindi → "हेलो" (phonetic)
- Korean "안녕하세요" to Hindi → "अन्न्योंग हासेयो" (phonetic)

Input ($lineCount lines):
$text

Output MUST be a JSON array with EXACTLY $lineCount strings in $targetLanguage script."""
      else ->
        """Translate the following $lineCount lines to $targetLanguage.

IMPORTANT:
- Provide natural, accurate translation
- Maintain poetic flow and meaning
- Keep punctuation appropriate for target language
- Preserve line-by-line structure exactly
- For song lyrics, prioritize singability

Input ($lineCount lines):
$text

Output MUST be a JSON array with EXACTLY $lineCount strings."""
    }
}
