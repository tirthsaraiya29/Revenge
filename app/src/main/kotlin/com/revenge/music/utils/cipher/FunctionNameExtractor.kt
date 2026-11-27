package com.revenge.music.utils.cipher

/**
 * Structural extraction of the two player-JS functions the cipher needs.
 *
 * YouTube ships its decipher routines as minified top-level functions. The *signature* function
 * takes the obfuscated `s` value and permutes its characters; the *n* function (throttling
 * parameter) is a different function that also splits, permutes and rejoins its argument but
 * always contains a loop. This extractor finds both by shape, without executing the player.
 */
object FunctionNameExtractor {

  /** A parsed function definition with its source span (including braces). */
  data class FunctionDef(
    val name: String?,
    val params: List<String>,
    val body: String,
    val start: Int,
    val end: Int,
  )

  private val IDENTIFIER = Regex("""^[A-Za-z_$][\w$]*$""")
  private val NUMBER = Regex("""^(?:0x[0-9A-Fa-f]+|-?\d+(?:\.\d+)?)$""")

  private val FUNCTION_REGEX =
    Regex(
      """(?:function\s+([A-Za-z_$][\w$]*)\s*\(([^)]*)\)""" +
        """|([A-Za-z_$][\w$]*)\s*=\s*function\s*\(([^)]*)\)""" +
        """|([A-Za-z_$][\w$]*)\s*:\s*function\s*\(([^)]*)\))\s*\{"""
    )

  private val SIGNATURE_TIMESTAMP =
    Regex("""signatureTimestamp\s*[:=]\s*"?(\d{3,6})"?|["']sts["']\s*:\s*(\d{3,6})""")

  /** Every function definition discoverable by declaration, assignment or object property. */
  fun extractFunctions(playerJs: String): List<FunctionDef> {
    val result = ArrayList<FunctionDef>()
    val seenStarts = HashSet<Int>()
    for (match in FUNCTION_REGEX.findAll(playerJs)) {
      val braceIndex = match.range.last
      if (braceIndex !in playerJs.indices || playerJs[braceIndex] != '{') continue
      if (!seenStarts.add(match.range.first)) continue
      val end = matchingBrace(playerJs, braceIndex)
      if (end < 0) continue
      val namedDeclaration = match.groupValues[1]
      val assigned = match.groupValues[3]
      val property = match.groupValues[5]
      val name = namedDeclaration.ifEmpty { assigned.ifEmpty { property } }.takeIf { it.isNotEmpty() }
      val paramsText =
        when {
          namedDeclaration.isNotEmpty() -> match.groupValues[2]
          assigned.isNotEmpty() -> match.groupValues[4]
          else -> match.groupValues[6]
        }
      result.add(
        FunctionDef(
          name = name,
          params = splitTopLevel(paramsText).map { it.trim() }.filter { it.isNotEmpty() },
          body = playerJs.substring(braceIndex + 1, end),
          start = match.range.first,
          end = end + 1,
        )
      )
    }
    return result
  }

  /** Best candidate for the `s` decipher function, or null when the player is unrecognised. */
  fun findSignatureFunction(playerJs: String): FunctionDef? {
    var best: FunctionDef? = null
    var bestScore = 0
    for (def in extractFunctions(playerJs)) {
      if (!hasSplitJoin(def.body)) continue
      if (def.params.size !in 1..3) continue
      val body = def.body
      var score = 0
      if (body.contains(".reverse()")) score += 4
      if (body.contains(".splice(")) score += 4
      if (body.contains(".slice(")) score += 2
      if (body.contains(".push(")) score += 2
      if (body.contains('%')) score += 1
      if (body.contains("while(")) score -= 4
      if (body.contains("for(")) score -= 1
      if (body.contains(".join(\"\")") || body.contains(".join('')")) score += 1
      if (score > bestScore) {
        bestScore = score
        best = def
      }
    }
    return best?.takeIf { bestScore > 0 }
  }

  /** Best candidate for the `n` throttling transform, or null. */
  fun findNFunction(
    playerJs: String,
    exclude: FunctionDef? = findSignatureFunction(playerJs),
  ): FunctionDef? {
    var best: FunctionDef? = null
    var bestScore = 0
    for (def in extractFunctions(playerJs)) {
      if (!hasSplitJoin(def.body)) continue
      if (def.params.size !in 1..3) continue
      if (exclude != null && def.start == exclude.start) continue
      val body = def.body
      var score = 0
      if (body.contains("while(")) score += 5
      if (body.contains("for(")) score += 3
      if (body.contains('%')) score += 2
      if (body.contains('[')) score += 1
      if (body.contains(".reverse()")) score -= 4
      if (body.contains(".splice(")) score -= 3
      if (score > bestScore) {
        bestScore = score
        best = def
      }
    }
    return best?.takeIf { bestScore > 0 }
  }

  /**
   * Renders the constant-bearing call site for [functionName] as e.g. `Fn(48,5831,INPUT)`.
   *
   * The player invokes its decipher routine as `Fn(<literal>, <literal>, <cipher>)`; keeping the
   * literals lets the runtime call the same variant the player itself uses. Falls back to
   * `Fn(INPUT)` when no such call site exists.
   */
  fun findSignatureCallDescriptor(playerJs: String, functionName: String): String? {
    if (!IDENTIFIER.matches(functionName)) return null
    val regex = Regex("""(?<![\w$.])""" + Regex.escape(functionName) + """\s*\(([^()]*)\)""")
    var best: String? = null
    var bestArity = 0
    for (match in regex.findAll(playerJs)) {
      val prefix = playerJs.substring(0, match.range.first).trimEnd()
      if (prefix.endsWith("function")) continue // the declaration itself
      if (prefix.endsWith(".")) continue // a method with the same short name
      val args = splitTopLevel(match.groupValues[1]).map { it.trim() }
      if (args.isEmpty() || args.size > 4) continue
      val inputArg = args.last()
      if (inputArg.isEmpty() || NUMBER.matches(inputArg)) continue
      val leading = args.dropLast(1)
      if (leading.any { !NUMBER.matches(it) }) continue
      val descriptor = functionName + "(" + (leading + "INPUT").joinToString(",") + ")"
      if (leading.size >= bestArity) {
        bestArity = leading.size
        best = descriptor
      }
    }
    return best ?: (functionName + "(INPUT)")
  }

  /** Signature timestamp (`sts`) embedded in the player, if present. */
  fun findSignatureTimestamp(playerJs: String): Int? {
    val match = SIGNATURE_TIMESTAMP.find(playerJs) ?: return null
    val value = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: return null
    return value.toIntOrNull()
  }

  /** All named functions, useful for diagnostics and cache invalidation. */
  fun extractFunctionNames(playerJs: String): Set<String> =
    extractFunctions(playerJs).mapNotNull { it.name }.toSet()

  private fun hasSplitJoin(body: String): Boolean {
    val split = body.contains(".split(\"\")") || body.contains(".split('')")
    val join = body.contains(".join(\"\")") || body.contains(".join('')")
    return split && join
  }

  private fun matchingBrace(source: String, openIndex: Int): Int {
    var depth = 0
    var index = openIndex
    while (index < source.length) {
      val char = source[index]
      when (char) {
        '"',
        '\'' -> {
          index = skipQuoted(source, index, char)
          continue
        }
        '`' -> {
          index = skipTemplate(source, index)
          continue
        }
        '/' -> {
          if (index + 1 < source.length) {
            val next = source[index + 1]
            if (next == '/') {
              index = skipLineComment(source, index + 2)
              continue
            }
            if (next == '*') {
              index = skipBlockComment(source, index + 2)
              continue
            }
          }
        }
        '{' -> depth++
        '}' -> {
          depth--
          if (depth == 0) return index
        }
      }
      index++
    }
    return -1
  }

  private fun skipQuoted(source: String, start: Int, quote: Char): Int {
    var index = start + 1
    while (index < source.length) {
      if (source[index] == '\\') {
        index += 2
        continue
      }
      if (source[index] == quote) return index + 1
      index++
    }
    return source.length
  }

  private fun skipTemplate(source: String, start: Int): Int {
    var index = start + 1
    while (index < source.length) {
      if (source[index] == '\\') {
        index += 2
        continue
      }
      if (source[index] == '`') return index + 1
      index++
    }
    return source.length
  }

  private fun skipLineComment(source: String, start: Int): Int {
    var index = start
    while (index < source.length && source[index] != '\n') index++
    return index
  }

  private fun skipBlockComment(source: String, start: Int): Int {
    var index = start
    while (index + 1 < source.length) {
      if (source[index] == '*' && source[index + 1] == '/') return index + 2
      index++
    }
    return source.length
  }

  /** Splits on commas that are not nested in brackets, braces, parentheses or strings. */
  private fun splitTopLevel(text: String): List<String> {
    if (text.isBlank()) return emptyList()
    val parts = ArrayList<String>()
    var depth = 0
    var start = 0
    var index = 0
    while (index < text.length) {
      val char = text[index]
      when (char) {
        '"',
        '\'' -> {
          index = skipQuoted(text, index, char)
          continue
        }
        '`' -> {
          index = skipTemplate(text, index)
          continue
        }
        '(',
        '[',
        '{' -> depth++
        ')',
        ']',
        '}' -> if (depth > 0) depth--
        ',' -> {
          if (depth == 0) {
            parts.add(text.substring(start, index))
            start = index + 1
          }
        }
      }
      index++
    }
    parts.add(text.substring(start))
    return parts
  }
}
