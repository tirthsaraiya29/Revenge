package com.revenge.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Runs(
  val runs: List<Run>?,
)

@Serializable
data class Run(
  val text: String,
  val navigationEndpoint: NavigationEndpoint?,
)

// Counts may use compact suffixes across locales (K/M/B/T plus CJK and Korean units).
private const val COMPACT_SUFFIX = "KkMmBbTt\\u4e07\\u842c\\u5104\\u4ebf\\u5146\\u5343\\ucc9c\\ub9cc\\uc5b5"
private val COUNT_REGEX =
  Regex("""\p{Nd}[\p{Nd}\s,.，．]*[$COMPACT_SUFFIX]*""")
private val SEPARATED_SUFFIX_REGEX = Regex("""\s+(?=[$COMPACT_SUFFIX]$)""")

/** Finds the human count (e.g. "1.2M views") inside a run list. */
fun Runs?.extractCountText(): String? {
  val texts = this?.runs?.map { it.text.trim() }?.filter { it.isNotEmpty() }.orEmpty()
  return texts.joinToString(separator = "").extractCountValue()
    ?: texts.firstNotNullOfOrNull { it.extractCountValue() }
}

private fun String.extractCountValue(): String? =
  COUNT_REGEX.find(this)?.value?.trim()?.replace(SEPARATED_SUFFIX_REGEX, "")?.takeIf { value ->
    value.any { it.isDigit() }
  }

/** Splits a run list on the " • " separators YouTube Music inserts between metadata parts. */
fun List<Run>.splitBySeparator(): List<List<Run>> {
  val sections = mutableListOf<List<Run>>()
  var current = mutableListOf<Run>()
  forEach { run ->
    if (run.text == " • ") {
      sections.add(current)
      current = mutableListOf()
    } else {
      current.add(run)
    }
  }
  sections.add(current)
  return sections
}

/** Drops a leading "Artist • " prefix when it is plain text rather than a linked run. */
fun List<List<Run>>.clean(): List<List<Run>> {
  val first = getOrNull(0)?.getOrNull(0)
  val keep = first?.navigationEndpoint != null || first?.text?.contains(Regex("[&,]")) != false
  return if (keep) this else drop(1)
}

fun List<Run>.oddElements(): List<Run> = filterIndexed { index, _ -> index % 2 == 0 }
