package com.revenge.music.lyrics

import com.atilika.kuromoji.ipadic.Tokenizer
import com.github.promeg.pinyinhelper.Pinyin
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.mocharealm.accompanist.lyrics.core.parser.AutoParser
import java.util.Locale

object LyricsUtils {

    private const val MAX_INPUT_LENGTH = 4_194_304
    private const val MAX_XML_DEPTH = 128
    private const val MAX_ENTRIES = 20_000
    private const val LOOKAHEAD_MS = 300L

    val TIME_REGEX: Regex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})]""")

    val LINE_REGEX: Regex = Regex("""^(?:\[(\d{2}):(\d{2})\.(\d{2,3})]\s?)+.+$""")

    private val richLineRegex = Regex("""^\[(\d{1,2}):(\d{2})\.(\d{2,3})](.*)$""")
    private val wordTagRegex = Regex("""<(\d{1,2}):(\d{2})\.(\d{2,3})>""")
    private val agentRegex = Regex("""\{agent:([^}]*)}""")
    private val backgroundPrefixRegex = Regex("""^\{bg}""")
    private val numericHexRegex = Regex("""&#x([0-9a-fA-F]+);""")
    private val numericDecRegex = Regex("""&#([0-9]+);""")
    private val whitespaceBeforePunctuation = Regex(
        """\s+([,.!?;:，。！？；：、（）《》〈〉【】『』「」])"""
    )

    private val autoParser: AutoParser by lazy { AutoParser() }
    private val japaneseTokenizer: Tokenizer by lazy { Tokenizer() }

    // ------------------------------------------------------------------ parsing

    fun hasSyncedLyrics(lyrics: String): Boolean = parseLyrics(lyrics).any { it.time > 0L }

    fun parseLyrics(lyrics: String): List<LyricsEntry> {
        if (lyrics.length > MAX_INPUT_LENGTH) return emptyList()
        val decoded = decode(lyrics)
        val looksStructured = decoded.startsWith("<") ||
            decoded.contains("<tt", ignoreCase = true) ||
            decoded.contains("<p begin", ignoreCase = true)
        if ((looksStructured || decoded.contains('<')) && exceedsXmlDepth(decoded)) {
            return emptyList()
        }
        if (decoded.isEmpty()) return emptyList()

        val rawLines = decoded.lines().filter { line ->
            line.isNotBlank() && !line.trimStart().startsWith("[offset:")
        }
        val rich = rawLines.any { line ->
            richLineRegex.matchEntire(line) != null && wordTagRegex.containsMatchIn(line)
        }
        if (rich) {
            return parseRich(rawLines).sorted().take(MAX_ENTRIES)
        }

        if (looksStructured || safeCanParse(decoded)) {
            parseWithAccompanist(decoded, looksStructured)?.let { return it.sorted().take(MAX_ENTRIES) }
        }

        return parseStandard(rawLines).sorted().take(MAX_ENTRIES)
    }

    fun findCurrentLineIndex(lines: List<LyricsEntry>, position: Long): Int {
        val boundary = position + LOOKAHEAD_MS
        for (index in lines.indices) {
            if (lines[index].time >= boundary) return index - 1
        }
        return lines.lastIndex
    }

    private fun safeCanParse(content: String): Boolean = try {
        autoParser.canParse(content)
    } catch (_: Exception) {
        false
    }

    private fun decode(input: String): String {
        var text = input.trim()
        if (text.startsWith('"')) text = text.removePrefix("\"")
        if (text.endsWith('"')) text = text.removeSuffix("\"")
        text = text
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
        text = decodeEntities(text)
        return text.trim()
    }

    private fun decodeEntities(input: String): String {
        var text = input
        text = numericHexRegex.replace(text) { match ->
            val code = match.groupValues[1].toIntOrNull(16)
            if (code != null && code in 0..0x10FFFF) String(Character.toChars(code)) else match.value
        }
        text = numericDecRegex.replace(text) { match ->
            val code = match.groupValues[1].toIntOrNull()
            if (code != null && code in 0..0x10FFFF) String(Character.toChars(code)) else match.value
        }
        text = text
            .replace("&apos;", "'")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
        return text
    }

    private fun exceedsXmlDepth(text: String): Boolean {
        var depth = 0
        var cursor = 0
        while (cursor < text.length) {
            val open = text.indexOf('<', cursor)
            if (open < 0) break
            if (text.startsWith("<!--", open)) {
                val close = text.indexOf("-->", open + 4)
                if (close < 0) break
                cursor = close + 3
                continue
            }
            val close = text.indexOf('>', open + 1)
            if (close < 0) break
            val tag = text.substring(open + 1, close)
            cursor = close + 1
            when {
                tag.startsWith("?") || tag.startsWith("!") -> Unit
                tag.startsWith("/") -> if (depth > 0) depth--
                tag.endsWith("/") -> Unit
                tag.isNotEmpty() && (tag[0].isLetter() || tag[0] == '_') -> {
                    depth++
                    if (depth > MAX_XML_DEPTH) return true
                }
            }
        }
        return false
    }

    private fun parseStandard(rawLines: List<String>): List<LyricsEntry> {
        val entries = ArrayList<LyricsEntry>()
        var lastGroup: IntRange? = null

        for (line in rawLines) {
            if (entries.size >= MAX_ENTRIES) break
            if (line.startsWith("<") && line.trimEnd().endsWith(">")) {
                val words = parseCompanionWords(line)
                val group = lastGroup
                if (group != null && words != null) {
                    for (index in group) entries[index] = entries[index].copy(words = words)
                }
                lastGroup = null
                continue
            }

            val stamps = TIME_REGEX.findAll(line).toList()
            if (stamps.isEmpty() || stamps.first().range.first != 0) {
                lastGroup = null
                continue
            }
            val prefixEnd = stamps.last().range.last + 1
            val prefix = line.substring(0, prefixEnd)
            if (TIME_REGEX.replace(prefix, "").any { !it.isWhitespace() }) {
                lastGroup = null
                continue
            }

            var text = line.substring(prefixEnd)
            if (text.startsWith(" ")) text = text.removePrefix(" ")
            if (text.isEmpty()) {
                lastGroup = null
                continue
            }

            val original = text
            var isBackground = false
            if (backgroundPrefixRegex.containsMatchIn(text)) {
                isBackground = true
                text = backgroundPrefixRegex.replaceFirst(text, "")
            }
            val agentMatch = agentRegex.find(text)
            val agent = agentMatch?.groupValues?.getOrNull(1)
            if (agentMatch != null) text = text.removeRange(agentMatch.range)

            if (text.isBlank()) {
                if (stamps.size > 1 && original.isNotBlank()) {
                    text = original
                } else {
                    lastGroup = null
                    continue
                }
            }

            val firstNew = entries.size
            for (stamp in stamps) {
                entries.add(
                    LyricsEntry(
                        time = millisOf(stamp.groupValues[1], stamp.groupValues[2], stamp.groupValues[3]),
                        text = text,
                        agent = agent,
                        isBackground = isBackground,
                    )
                )
            }
            lastGroup = firstNew until entries.size
        }
        return entries
    }

    private fun parseCompanionWords(line: String): List<WordTimestamp>? {
        val inner = line.trim().removePrefix("<").removeSuffix(">")
        val words = inner.split("|").mapNotNull { part ->
            val segments = part.split(":")
            if (segments.size != 3) return@mapNotNull null
            val text = segments[0].trim()
            if (text.isEmpty()) return@mapNotNull null
            WordTimestamp(
                text = text,
                startTime = segments[1].trim().toDoubleOrNull() ?: 0.0,
                endTime = segments[2].trim().toDoubleOrNull() ?: 0.0,
            )
        }
        return words.takeIf { it.isNotEmpty() }
    }

    private fun parseRich(rawLines: List<String>): List<LyricsEntry> {
        val entries = ArrayList<LyricsEntry>()
        for ((index, line) in rawLines.withIndex()) {
            if (entries.size >= MAX_ENTRIES) break
            val match = richLineRegex.matchEntire(line) ?: continue
            val lineStart = secondsOf(match.groupValues[1], match.groupValues[2], match.groupValues[3])
            var content = match.groupValues[4]

            var isBackground = false
            if (backgroundPrefixRegex.containsMatchIn(content)) {
                isBackground = true
                content = backgroundPrefixRegex.replaceFirst(content, "")
            }
            val agentMatch = agentRegex.find(content)
            val agent = agentMatch?.groupValues?.getOrNull(1)
            if (agentMatch != null) content = content.removeRange(agentMatch.range)

            val tags = wordTagRegex.findAll(content).toList()
            val words = if (tags.isEmpty()) {
                null
            } else {
                val starts = tags.mapNotNull { tag ->
                    val wordEnd = content.indexOf('<', tag.range.last + 1)
                        .let { if (it < 0) content.length else it }
                    val wordText = content.substring(tag.range.last + 1, wordEnd).trim()
                    if (wordText.isEmpty()) {
                        null
                    } else {
                        wordText to secondsOf(tag.groupValues[1], tag.groupValues[2], tag.groupValues[3])
                    }
                }
                if (starts.isEmpty()) {
                    null
                } else {
                    val nextLineStart = rawLines.getOrNull(index + 1)?.let { next ->
                        richLineRegex.matchEntire(next)?.let {
                            secondsOf(it.groupValues[1], it.groupValues[2], it.groupValues[3])
                        }
                    }
                    starts.mapIndexed { wordIndex, (wordText, start) ->
                        val end = when {
                            wordIndex + 1 < starts.size -> starts[wordIndex + 1].second
                            nextLineStart != null -> nextLineStart
                            else -> lineStart + 0.5
                        }
                        WordTimestamp(wordText, start, end)
                    }
                }
            }

            val text = wordTagRegex.replace(content, "").trim()
            if (text.isEmpty()) continue
            entries.add(
                LyricsEntry(
                    time = (lineStart * 1000.0).toLong(),
                    text = text,
                    words = words,
                    agent = agent,
                    isBackground = isBackground,
                )
            )
        }
        return entries
    }

    private fun parseWithAccompanist(decoded: String, isStructured: Boolean): List<LyricsEntry>? {
        val synced = try {
            autoParser.parse(decoded)
        } catch (_: Exception) {
            return null
        }
        if (synced.lines.isEmpty()) return null

        val entries = ArrayList<LyricsEntry>()
        for (line in synced.lines) {
            when (line) {
                is KaraokeLine -> {
                    val text = line.syllables.joinToString("") { it.content }.trim()
                    if (text.isEmpty()) continue
                    val words = line.syllables.mapNotNull { syllable ->
                        val syllableText = syllable.content.trim()
                        if (syllableText.isEmpty()) {
                            null
                        } else {
                            WordTimestamp(
                                syllableText,
                                syllable.start / 1000.0,
                                syllable.end / 1000.0,
                            )
                        }
                    }.takeIf { it.isNotEmpty() }
                    val entry = LyricsEntry(
                        time = line.start.toLong(),
                        text = text,
                        words = words,
                        isBackground = line is KaraokeLine.AccompanimentKaraokeLine,
                    )
                    line.translation?.takeIf { it.isNotBlank() }?.let { entry.translatedTextFlow.value = it }
                    entries.add(entry)
                }

                is SyncedLine -> {
                    val text = line.content.trim()
                    if (text.isEmpty()) continue
                    val entry = LyricsEntry(time = line.start.toLong(), text = text)
                    line.translation?.takeIf { it.isNotBlank() }?.let { entry.translatedTextFlow.value = it }
                    entries.add(entry)
                }

                else -> Unit
            }
        }

        val rawLineCount = decoded.lines().count { line ->
            line.isNotBlank() && METADATA_PREFIXES.none { line.trimStart().startsWith(it) }
        }
        val hasWordTimings = entries.any { !it.words.isNullOrEmpty() }
        if (!isStructured && !hasWordTimings && entries.size < rawLineCount) return null
        return entries
    }

    private val METADATA_PREFIXES = listOf("[offset:", "[ar:", "[ti:", "[al:", "[by:")

    private fun millisOf(minutes: String, seconds: String, fraction: String): Long {
        val fractionValue = fraction.toLongOrNull() ?: 0L
        val fractionMs = if (fraction.length == 2) fractionValue * 10 else fractionValue
        return minutes.toLong() * 60_000 + seconds.toLong() * 1_000 + fractionMs
    }

    private fun secondsOf(minutes: String, seconds: String, fraction: String): Double {
        val fractionValue = fraction.toDoubleOrNull() ?: 0.0
        val fractionSeconds = if (fraction.length == 2) fractionValue / 100.0 else fractionValue / 1000.0
        return minutes.toLong() * 60.0 + seconds.toLong() + fractionSeconds
    }

    // ------------------------------------------------------------- romanisation

    fun katakanaToRomaji(katakana: String?): String = katakanaToRomaji(katakana, null)

    fun katakanaToRomaji(katakana: String?, nextKatakana: String? = null): String {
        if (katakana.isNullOrEmpty()) return ""
        val out = StringBuilder()
        var index = 0
        while (index < katakana.length) {
            val char = katakana[index]
            if (char == 'ッ') {
                if (!nextKatakana.isNullOrEmpty()) {
                    val mapped = matchKana(nextKatakana, 0)
                    if (mapped != null) {
                        if (mapped.first.isNotEmpty()) out.append(mapped.first[0].lowercaseChar())
                    } else {
                        out.append(nextKatakana[0].lowercaseChar())
                    }
                }
                index++
                continue
            }
            val matched = matchKana(katakana, index)
            if (matched != null) {
                out.append(matched.first)
                index += matched.second
            } else {
                out.append(char)
                index++
            }
        }
        return out.toString().lowercase()
    }

    private fun matchKana(source: String, index: Int): Pair<String, Int>? {
        if (index + 1 < source.length) {
            val pair = source.substring(index, index + 2)
            KATAKANA[pair]?.let { return it to 2 }
        }
        KATAKANA[source[index].toString()]?.let { return it to 1 }
        return null
    }

    suspend fun romanizeJapanese(text: String): String {
        if (text.isEmpty()) return ""
        val tokens = japaneseTokenizer.tokenize(text)
        val readings = tokens.map { token ->
            val reading = token.reading
            if (reading.isNullOrEmpty() || reading == "*") null else reading
        }
        return tokens.mapIndexed { index, token ->
            val base = readings[index] ?: token.surfaceForm
            katakanaToRomaji(base, readings.getOrNull(index + 1))
        }.joinToString(" ").lowercase()
    }

    suspend fun romanizeKorean(text: String): String {
        if (text.isEmpty()) return ""
        val out = StringBuilder()
        var pendingFinal = 0
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char in '가'..'힣') {
                val offset = char.code - 0xAC00
                val cho = offset / (21 * 28)
                val jung = (offset % (21 * 28)) / 28
                val jong = offset % 28
                if (pendingFinal != 0) {
                    val liaison = KOREAN_LIAISON[pendingFinal to cho]
                    if (liaison != null) {
                        out.append(liaison)
                        out.append(KOREAN_JUNG[jung])
                        pendingFinal = jong
                        index++
                        continue
                    }
                    out.append(KOREAN_JONG[pendingFinal])
                    pendingFinal = 0
                }
                out.append(KOREAN_CHO[cho])
                out.append(KOREAN_JUNG[jung])
                pendingFinal = jong
            } else {
                if (pendingFinal != 0) {
                    out.append(KOREAN_JONG[pendingFinal])
                    pendingFinal = 0
                }
                out.append(char)
            }
            index++
        }
        if (pendingFinal != 0) out.append(KOREAN_JONG[pendingFinal])
        return out.toString()
    }

    suspend fun romanizeChinese(text: String): String {
        if (text.isEmpty()) return ""
        val out = StringBuilder()
        for (char in text) {
            if (char in '一'..'鿿') {
                out.append(Pinyin.toPinyin(char).lowercase(Locale.getDefault()))
                out.append(' ')
            } else {
                out.append(char)
            }
        }
        return whitespaceBeforePunctuation.replace(out.toString(), "$1").trim()
    }

    suspend fun romanizeCyrillic(text: String): String? {
        if (text.isEmpty()) return null
        val cyrillic = text.filter { it in 'Ѐ'..'ӿ' }
        if (cyrillic.isEmpty()) return null
        if (cyrillic.length == 1 && (cyrillic[0] == 'е' || cyrillic[0] == 'Е')) return null
        val alphabet = when {
            isRussian(text) -> CyrillicAlphabet.RUSSIAN
            isUkrainian(text) -> CyrillicAlphabet.UKRAINIAN
            isSerbian(text) -> CyrillicAlphabet.SERBIAN
            isBulgarian(text) -> CyrillicAlphabet.BULGARIAN
            isBelarusian(text) -> CyrillicAlphabet.BELARUSIAN
            isKyrgyz(text) -> CyrillicAlphabet.KYRGYZ
            isMacedonian(text) -> CyrillicAlphabet.MACEDONIAN
            else -> return null
        }
        return transliterateCyrillic(text, alphabet)
    }

    suspend fun romanizeHindi(text: String): String =
        transliterateIndic(text, HINDI_PAIRS, HINDI_SINGLES, handleAddak = false)

    suspend fun romanizePunjabi(text: String): String =
        transliterateIndic(text, PUNJABI_PAIRS, PUNJABI_SINGLES, handleAddak = true)

    fun isRussian(text: String): Boolean = cyrillicMatches(text, RUSSIAN_SET)

    fun isUkrainian(text: String): Boolean = cyrillicMatches(text, UKRAINIAN_SET)

    fun isSerbian(text: String): Boolean = cyrillicMatches(text, SERBIAN_SET)

    fun isBulgarian(text: String): Boolean = cyrillicMatches(text, BULGARIAN_SET)

    fun isBelarusian(text: String): Boolean = cyrillicMatches(text, BELARUSIAN_SET)

    fun isKyrgyz(text: String): Boolean = cyrillicMatches(text, KYRGYZ_SET)

    fun isMacedonian(text: String): Boolean = cyrillicMatches(text, MACEDONIAN_SET)

    fun isJapanese(text: String): Boolean =
        text.any { it in '぀'..'ゟ' || it in '゠'..'ヿ' || it in '一'..'鿿' }

    fun isKorean(text: String): Boolean = text.any { it in '가'..'힣' }

    fun isChinese(text: String): Boolean {
        if (text.isEmpty()) return false
        var ideographs = 0
        var kana = 0
        for (char in text) {
            when {
                char in '一'..'鿿' -> ideographs++
                char in '぀'..'ゟ' || char in '゠'..'ヿ' -> kana++
            }
        }
        if (ideographs == 0) return false
        return kana.toDouble() / text.length < 0.1
    }

    fun isHindi(text: String): Boolean = text.any { it in 'ऀ'..'ॿ' }

    fun isPunjabi(text: String): Boolean = text.any { it in '਀'..'੿' }

    private fun cyrillicMatches(text: String, allowed: Set<Char>): Boolean {
        var found = false
        for (char in text) {
            if (char in 'Ѐ'..'ӿ') {
                if (char !in allowed) return false
                found = true
            }
        }
        return found
    }

    private fun transliterateIndic(
        text: String,
        pairs: Map<String, String>,
        singles: Map<Char, String>,
        handleAddak: Boolean,
    ): String {
        val out = StringBuilder()
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (handleAddak && char == 'ੱ') {
                if (index + 1 < text.length) {
                    val mapped = indicMatch(text, index + 1, pairs, singles)
                    if (mapped != null && mapped.first.isNotEmpty()) {
                        out.append(mapped.first[0].lowercaseChar())
                    }
                }
                index++
                continue
            }
            val mapped = indicMatch(text, index, pairs, singles)
            if (mapped != null) {
                out.append(mapped.first)
                index += mapped.second
            } else {
                out.append(char)
                index++
            }
        }
        return out.toString()
    }

    private fun indicMatch(
        text: String,
        index: Int,
        pairs: Map<String, String>,
        singles: Map<Char, String>,
    ): Pair<String, Int>? {
        if (index + 1 < text.length) {
            pairs[text.substring(index, index + 2)]?.let { return it to 2 }
        }
        singles[text[index]]?.let { return it to 1 }
        return null
    }

    // ------------------------------------------------------------- cyrillic core

    private enum class CyrillicAlphabet { RUSSIAN, UKRAINIAN, SERBIAN, BULGARIAN, BELARUSIAN, KYRGYZ, MACEDONIAN }

    private val CYRILLIC_VOWELS = "АаЕеЄєИиІіЇїОоУуЮюЯяЫыЭэ".toSet()

    private val GENERAL_CYRILLIC: Map<Char, String> = mapOf(
        'А' to "A", 'Б' to "B", 'В' to "V", 'Г' to "G", 'Д' to "D", 'Е' to "E",
        'Ё' to "Yo", 'Ж' to "Zh", 'З' to "Z", 'И' to "I", 'Й' to "Y", 'К' to "K",
        'Л' to "L", 'М' to "M", 'Н' to "N", 'О' to "O", 'П' to "P", 'Р' to "R",
        'С' to "S", 'Т' to "T", 'У' to "U", 'Ф' to "F", 'Х' to "Kh", 'Ц' to "Ts",
        'Ч' to "Ch", 'Ш' to "Sh", 'Щ' to "Shch", 'Ъ' to "ʺ", 'Ы' to "Y", 'Ь' to "ʹ",
        'Э' to "E", 'Ю' to "Yu", 'Я' to "Ya", 'І' to "I", 'Ї' to "Yi", 'Є' to "Ye",
        'Ґ' to "G", 'Ў' to "W", 'Ү' to "Ü", 'Ң' to "N", 'Ө' to "O",
        'Ђ' to "Đ", 'Ј' to "J", 'Љ' to "Lj", 'Њ' to "Nj", 'Ћ' to "Ć", 'Џ' to "Dž",
        'Ѓ' to "Gj", 'Ѕ' to "Dz", 'Ќ' to "Kj",
    )

    private fun generalChar(char: Char): String? {
        val upper = char.uppercaseChar()
        val base = GENERAL_CYRILLIC[upper] ?: return null
        return if (char.isUpperCase()) base else base.lowercase()
    }

    private fun transliterateCyrillic(text: String, alphabet: CyrillicAlphabet): String {
        val out = StringBuilder()
        var index = 0
        while (index < text.length) {
            if (text[index].isLetter()) {
                var end = index
                while (end < text.length && text[end].isLetter()) end++
                out.append(transliterateWord(text.substring(index, end), alphabet))
                index = end
            } else {
                out.append(text[index])
                index++
            }
        }
        return out.toString()
    }

    private fun transliterateWord(word: String, alphabet: CyrillicAlphabet): String = when (alphabet) {
        CyrillicAlphabet.RUSSIAN -> russianWord(word)
        CyrillicAlphabet.UKRAINIAN -> ukrainianWord(word)
        CyrillicAlphabet.SERBIAN -> mappedWord(word, SERBIAN_OVERRIDES)
        CyrillicAlphabet.BULGARIAN -> mappedWord(word, BULGARIAN_OVERRIDES)
        CyrillicAlphabet.BELARUSIAN -> belarusianWord(word)
        CyrillicAlphabet.KYRGYZ -> mappedWord(word, KYRGYZ_OVERRIDES)
        CyrillicAlphabet.MACEDONIAN -> mappedWord(word, MACEDONIAN_OVERRIDES)
    }

    private fun russianWord(word: String): String {
        var text = word
            .replace("ого", "ovo")
            .replace("Ого", "Ovo")
            .replace("его", "evo")
            .replace("Его", "Evo")
        text = when {
            text.startsWith("Е") -> "Ye" + text.substring(1)
            text.startsWith("е") -> "ye" + text.substring(1)
            else -> text
        }
        return generalWord(text)
    }

    private fun ukrainianWord(word: String): String {
        val out = StringBuilder()
        for (index in word.indices) {
            val char = word[index]
            when (char) {
                'Г' -> out.append("H")
                'г' -> out.append("h")
                'Ґ' -> out.append("G")
                'ґ' -> out.append("g")
                'Є' -> out.append("Ye")
                'є' -> out.append("ye")
                'І' -> out.append("I")
                'і' -> out.append("i")
                'Ї' -> out.append("Yi")
                'ї' -> out.append("yi")
                'Ю', 'ю', 'Я', 'я' -> {
                    val previous = word.getOrNull(index - 1)
                    val afterConsonant = previous != null && previous.isLetter() && previous !in CYRILLIC_VOWELS
                    val isYu = char == 'Ю' || char == 'ю'
                    if (afterConsonant) {
                        out.append(
                            when {
                                isYu && char.isUpperCase() -> "Iu"
                                isYu -> "iu"
                                char.isUpperCase() -> "Ia"
                                else -> "ia"
                            }
                        )
                    } else {
                        out.append(generalChar(char) ?: char.toString())
                    }
                }
                else -> out.append(generalChar(char) ?: char.toString())
            }
        }
        return out.toString()
    }

    private fun belarusianWord(word: String): String {
        var text = word
        text = when {
            text.startsWith("Е") -> "Ye" + text.substring(1)
            text.startsWith("е") -> "ye" + text.substring(1)
            else -> text
        }
        return mappedWord(text, mapOf('Г' to "H", 'Ў' to "W"))
    }

    private fun generalWord(word: String): String {
        val out = StringBuilder()
        for (char in word) out.append(generalChar(char) ?: char.toString())
        return out.toString()
    }

    private fun mappedWord(word: String, overrides: Map<Char, String>): String {
        val out = StringBuilder()
        for (char in word) {
            val override = overrides[char.uppercaseChar()]
            if (override != null) {
                out.append(if (char.isUpperCase()) override else override.lowercase())
            } else {
                out.append(generalChar(char) ?: char.toString())
            }
        }
        return out.toString()
    }

    private val SERBIAN_OVERRIDES = mapOf(
        'Ж' to "Ž", 'Љ' to "Lj", 'Њ' to "Nj", 'Ц' to "C", 'Ч' to "Č", 'Џ' to "Dž",
        'Ш' to "Š", 'Х' to "H",
    )

    private val BULGARIAN_OVERRIDES = mapOf(
        'Ж' to "Zh", 'Ц' to "Ts", 'Ч' to "Ch", 'Ш' to "Sh", 'Щ' to "Sht", 'Ъ' to "A",
        'Ь' to "Y", 'Ю' to "Yu", 'Я' to "Ya",
    )

    private val KYRGYZ_OVERRIDES = mapOf('Ү' to "Ü", 'Ы' to "Y")

    private val MACEDONIAN_OVERRIDES = mapOf(
        'Ѓ' to "Gj", 'Ѕ' to "Dz", 'И' to "I", 'Ј' to "J", 'Љ' to "Lj", 'Њ' to "Nj",
        'Ќ' to "Kj", 'Џ' to "Dž", 'Ч' to "Č", 'Ш' to "Sh", 'Ж' to "Zh", 'Ц' to "C",
        'Х' to "H",
    )

    private fun alphabetString(value: String): Set<Char> = value.toSet()

    private val RUSSIAN_SET = alphabetString(
        "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя"
    )
    private val UKRAINIAN_SET = alphabetString(
        "АБВГҐДЕЄЖЗИІЇЙКЛМНОПРСТУФХЦЧШЩЬЮЯабвгґдеєжзиіїйклмнопрстуфхцчшщьюя"
    )
    private val SERBIAN_SET = alphabetString(
        "АБВГДЂЕЖЗИЈКЛЉМНЊОПРСТЋУФХЦЧЏШабвгдђежзијклљмнњопрстћуфхцчџш"
    )
    private val BULGARIAN_SET = alphabetString(
        "АБВГДЕЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЬЮЯабвгдежзийклмнопрстуфхцчшщъьюя"
    )
    private val BELARUSIAN_SET = alphabetString(
        "АБВГДЕЁЖЗІЙКЛМНОПРСТУЎФХЦЧШЫЬЭЮЯабвгдеёжзійклмнопрстуўфхцчшыьэюя"
    )
    private val KYRGYZ_SET = alphabetString(
        "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯҢңӨөҮүабвгдеёжзийклмнопрстуфхцчшщъыьэюя"
    )
    private val MACEDONIAN_SET = alphabetString(
        "АБВГДЃЕЖЗЅИЈКЛЉМНЊОПРСТЌУФХЦЧЏШабвгдѓежзѕијклљмнњопрстќуфхцчџш"
    )

    // -------------------------------------------------------------- kana tables

    private val KATAKANA: Map<String, String> = buildMap {
        fun add(kana: String, romaji: String) { put(kana, romaji) }

        // youon
        add("キャ", "kya"); add("キュ", "kyu"); add("キョ", "kyo")
        add("シャ", "sha"); add("シュ", "shu"); add("ショ", "sho")
        add("チャ", "cha"); add("チュ", "chu"); add("チョ", "cho")
        add("ニャ", "nya"); add("ニュ", "nyu"); add("ニョ", "nyo")
        add("ヒャ", "hya"); add("ヒュ", "hyu"); add("ヒョ", "hyo")
        add("ミャ", "mya"); add("ミュ", "myu"); add("ミョ", "myo")
        add("リャ", "rya"); add("リュ", "ryu"); add("リョ", "ryo")
        add("ギャ", "gya"); add("ギュ", "gyu"); add("ギョ", "gyo")
        add("ジャ", "ja"); add("ジュ", "ju"); add("ジョ", "jo")
        add("ヂャ", "ja"); add("ヂュ", "ju"); add("ヂョ", "jo")
        add("ビャ", "bya"); add("ビュ", "byu"); add("ビョ", "byo")
        add("ピャ", "pya"); add("ピュ", "pyu"); add("ピョ", "pyo")

        // base and voiced kana
        add("ア", "a"); add("イ", "i"); add("ウ", "u"); add("エ", "e"); add("オ", "o")
        add("カ", "ka"); add("キ", "ki"); add("ク", "ku"); add("ケ", "ke"); add("コ", "ko")
        add("ガ", "ga"); add("ギ", "gi"); add("グ", "gu"); add("ゲ", "ge"); add("ゴ", "go")
        add("サ", "sa"); add("シ", "shi"); add("ス", "su"); add("セ", "se"); add("ソ", "so")
        add("ザ", "za"); add("ジ", "ji"); add("ズ", "zu"); add("ゼ", "ze"); add("ゾ", "zo")
        add("タ", "ta"); add("チ", "chi"); add("ツ", "tsu"); add("テ", "te"); add("ト", "to")
        add("ダ", "da"); add("ヂ", "ji"); add("ヅ", "zu"); add("デ", "de"); add("ド", "do")
        add("ナ", "na"); add("ニ", "ni"); add("ヌ", "nu"); add("ネ", "ne"); add("ノ", "no")
        add("ハ", "ha"); add("ヒ", "hi"); add("フ", "fu"); add("ヘ", "he"); add("ホ", "ho")
        add("バ", "ba"); add("ビ", "bi"); add("ブ", "bu"); add("ベ", "be"); add("ボ", "bo")
        add("パ", "pa"); add("ピ", "pi"); add("プ", "pu"); add("ペ", "pe"); add("ポ", "po")
        add("マ", "ma"); add("ミ", "mi"); add("ム", "mu"); add("メ", "me"); add("モ", "mo")
        add("ヤ", "ya"); add("ユ", "yu"); add("ヨ", "yo")
        add("ラ", "ra"); add("リ", "ri"); add("ル", "ru"); add("レ", "re"); add("ロ", "ro")
        add("ワ", "wa"); add("ヰ", "wi"); add("ヱ", "we"); add("ヲ", "o"); add("ン", "n")
        add("ヴ", "vu")

        // small kana and marks
        add("ァ", "a"); add("ィ", "i"); add("ゥ", "u"); add("ェ", "e"); add("ォ", "o")
        add("ャ", "ya"); add("ュ", "yu"); add("ョ", "yo"); add("ヮ", "wa"); add("ヵ", "ka"); add("ヶ", "ke")
        add("ー", "")
    }

    // ------------------------------------------------------------- hangul tables

    private val KOREAN_CHO = listOf(
        "g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s", "ss", "", "j", "jj",
        "ch", "k", "t", "p", "h",
    )
    private val KOREAN_JUNG = listOf(
        "a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa", "wae", "oe",
        "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i",
    )
    private val KOREAN_JONG = listOf(
        "", "k", "k", "ks", "n", "nj", "nh", "t", "l", "lk", "lm", "lb", "ls",
        "lt", "lp", "lh", "m", "p", "ps", "t", "t", "ng", "t", "t", "k", "t", "p", "h",
    )
    private val KOREAN_LIAISON: Map<Pair<Int, Int>, String> = buildMap {
        val released = mapOf(
            1 to "g", 2 to "kk", 4 to "n", 7 to "d", 8 to "r", 16 to "m", 17 to "b",
            19 to "s", 20 to "ss", 21 to "ng", 22 to "j", 23 to "ch", 24 to "k",
            25 to "t", 26 to "p", 27 to "h",
        )
        for ((jong, sound) in released) put(jong to 11, sound)
        put(4 to 5, "ll")
        put(8 to 4, "ll")
    }

    // -------------------------------------------------------------- indic tables

    private val HINDI_PAIRS = mapOf(
        "क्ष" to "ksh", "त्र" to "tr", "ज्ञ" to "gy", "श्र" to "shr",
        "क़" to "q", "ख़" to "kh", "ग़" to "g", "ज़" to "z", "ड़" to "r",
        "ढ़" to "rh", "फ़" to "f", "य़" to "y",
    )

    private val HINDI_SINGLES: Map<Char, String> = buildMap {
        fun add(char: Char, value: String) { put(char, value) }
        add('अ', "a"); add('आ', "aa"); add('इ', "i"); add('ई', "ee"); add('उ', "u")
        add('ऊ', "oo"); add('ऋ', "ri"); add('ए', "e"); add('ऐ', "ai"); add('ओ', "o")
        add('औ', "au"); add('ऍ', "e"); add('ऑ', "o")
        add('क', "k"); add('ख', "kh"); add('ग', "g"); add('घ', "gh"); add('ङ', "ng")
        add('च', "ch"); add('छ', "chh"); add('ज', "j"); add('झ', "jh"); add('ञ', "n")
        add('ट', "t"); add('ठ', "th"); add('ड', "d"); add('ढ', "dh"); add('ण', "n")
        add('त', "t"); add('थ', "th"); add('द', "d"); add('ध', "dh"); add('न', "n")
        add('प', "p"); add('फ', "ph"); add('ब', "b"); add('भ', "bh"); add('म', "m")
        add('य', "y"); add('र', "r"); add('ल', "l"); add('व', "v"); add('श', "sh")
        add('ष', "sh"); add('स', "s"); add('ह', "h"); add('ळ', "l")
        add('ा', "aa"); add('ि', "i"); add('ी', "ee"); add('ु', "u"); add('ू', "oo")
        add('ृ', "ri"); add('े', "e"); add('ै', "ai"); add('ो', "o"); add('ौ', "au")
        add('ं', "n"); add('ँ', "n"); add('ः', "h"); add('्', ""); add('़', "")
        add('ॐ', "Om")
        add('०', "0"); add('१', "1"); add('२', "2"); add('३', "3"); add('४', "4")
        add('५', "5"); add('६', "6"); add('७', "7"); add('८', "8"); add('९', "9")
    }

    private val PUNJABI_PAIRS = mapOf(
        "ਖ਼" to "kh", "ਗ਼" to "g", "ਜ਼" to "z", "ਫ਼" to "f",
    )

    private val PUNJABI_SINGLES: Map<Char, String> = buildMap {
        fun add(char: Char, value: String) { put(char, value) }
        add('ਅ', "a"); add('ਆ', "aa"); add('ਇ', "i"); add('ਈ', "ee"); add('ਉ', "u")
        add('ਊ', "oo"); add('ਏ', "e"); add('ਐ', "ai"); add('ਓ', "o"); add('ਔ', "au")
        add('ਕ', "k"); add('ਖ', "kh"); add('ਗ', "g"); add('ਘ', "gh"); add('ਙ', "ng")
        add('ਚ', "ch"); add('ਛ', "chh"); add('ਜ', "j"); add('ਝ', "jh"); add('ਞ', "n")
        add('ਟ', "t"); add('ਠ', "th"); add('ਡ', "d"); add('ਢ', "dh"); add('ਣ', "n")
        add('ਤ', "t"); add('ਥ', "th"); add('ਦ', "d"); add('ਧ', "dh"); add('ਨ', "n")
        add('ਪ', "p"); add('ਫ', "ph"); add('ਬ', "b"); add('ਭ', "bh"); add('ਮ', "m")
        add('ਯ', "y"); add('ਰ', "r"); add('ਲ', "l"); add('ਵ', "v"); add('ੜ', "r")
        add('ਸ਼', "sh"); add('ਸ', "s"); add('ਹ', "h"); add('ਲ਼', "l")
        add('ਾ', "aa"); add('ਿ', "i"); add('ੀ', "ee"); add('ੁ', "u"); add('ੂ', "oo")
        add('ੇ', "e"); add('ੈ', "ai"); add('ੋ', "o"); add('ੌ', "au")
        add('ੰ', "n"); add('ਂ', "n"); add('ਃ', "h"); add('੍', ""); add('਼', "")
        add('ੴ', "Ek Onkar")
        add('੦', "0"); add('੧', "1"); add('੨', "2"); add('੩', "3"); add('੪', "4")
        add('੫', "5"); add('੬', "6"); add('੭', "7"); add('੮', "8"); add('੯', "9")
    }
}
