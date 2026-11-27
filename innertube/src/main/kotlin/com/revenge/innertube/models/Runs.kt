package com.revenge.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Runs(
  val runs: List<Run>?,
)

@Serializable
data class Run(
  val text: String,
  val navigationEndpoint: NavigationEndpoint? = null,
)

private const val COUNT_SUFFIXES = "KMBT万萬億亿兆千천만억"
private const val COUNT_SEPARATORS = " \\s,.\\uFF0C\\uFF0E\\u3001"

private val countRegex = Regex("\\d+(?:[$COUNT_SEPARATORS]\\d+)*(?:\\s?[$COUNT_SUFFIXES])?")
private val spaceBeforeSuffixRegex = Regex("\\s(?=[$COUNT_SUFFIXES])")

private fun findCountToken(text: String): String? {
  val match = countRegex.find(text)?.value ?: return null
  return match.replace(spaceBeforeSuffixRegex, "")
}

fun Runs?.extractCountText(): String? {
  val runs = this?.runs ?: return null
  val combined = runs.map { it.text.trim() }.filter { it.isNotEmpty() }.joinToString("")
  findCountToken(combined)?.let {
    return it
  }
  return runs.firstNotNullOfOrNull { findCountToken(it.text.trim()) }
}

fun List<Run>.splitBySeparator(): List<List<Run>> {
  val groups = mutableListOf<MutableList<Run>>(mutableListOf())
  for (run in this) {
    if (run.text == " • ") {
      groups.add(mutableListOf())
    } else {
      groups.last().add(run)
    }
  }
  return groups
}

fun List<List<Run>>.clean(): List<List<Run>> {
  val first = firstOrNull() ?: return this
  val firstRun = first.firstOrNull()
  val text = firstRun?.text
  val looksLikeLinkedOrMultiValue = text != null && (text.contains('&') || text.contains(','))
  val drop = firstRun?.navigationEndpoint == null && !looksLikeLinkedOrMultiValue
  return if (drop) drop(1) else this
}

fun List<Run>.oddElements(): List<Run> = filterIndexed { index, _ -> index % 2 == 0 }
