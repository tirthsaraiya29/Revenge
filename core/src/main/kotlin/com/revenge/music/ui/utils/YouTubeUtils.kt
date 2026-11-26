package com.revenge.music.ui.utils

/** Rewrites a thumbnail URL to request a variant of the given size, when the host supports it. */
fun String.resize(
  width: Int? = null,
  height: Int? = null,
): String {
  if (width == null && height == null) return this

  if (contains("i.ytimg.com")) {
    // maxresdefault is not available for every video; callers fall back along the ytimg ladder.
    val target = if ((width ?: 0) >= 1200) "maxresdefault.jpg" else "hqdefault.jpg"
    return replace(
      Regex("(default|mqdefault|hqdefault|sddefault|maxresdefault|hq720)\\.jpg"),
      target,
    )
  }

  if (contains("googleusercontent.com") && contains("=w")) {
    val base = substringBefore("=w")
    val size = if ((width ?: 0) >= 1000 || (height ?: 0) >= 1000) 1200 else 500
    return "$base=w$size-h$size"
  }

  if (contains("yt3.ggpht.com")) {
    // The size lives in the "=s…" suffix; avatar ids may themselves contain "-s".
    return substringBefore('=') + "=s${width ?: height}"
  }

  "https://lh\\d\\.googleusercontent\\.com/.*".toRegex().matchEntire(this)?.let {
    val size = if ((width ?: 0) >= 1000 || (height ?: 0) >= 1000) 1200 else 500
    return substringBefore('=') + "=w$size-h$size"
  }

  return this
}

/**
 * The next thumbnail variant worth retrying when a YouTube still is missing, or null when the
 * URL is not a ytimg still. `hq720` is preferred over `hqdefault` because both share maxresdefault's
 * 16:9 frame, while hqdefault letterboxes the image inside 4:3.
 */
fun ytimgFallbackUrl(url: String): String? {
  if (!url.contains("ytimg.com")) return null
  val path = url.substringBefore('?')
  val next =
    when {
      path.endsWith("/maxresdefault.jpg") || path.endsWith("/sddefault.jpg") -> "hq720.jpg"
      path.endsWith("/hq720.jpg") -> "hqdefault.jpg"
      else -> return null
    }
  return path.substringBeforeLast('/') + "/" + next
}
