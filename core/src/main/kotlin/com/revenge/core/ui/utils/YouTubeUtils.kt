package com.revenge.core.ui.utils

private val YT_IMG_SIZE =
  Regex("(?:maxresdefault|sddefault|hqdefault|mqdefault|hq720|default)\\.jpg")
private val GOOGLE_CONTENT_HOST = Regex("https://lh\\d+\\.googleusercontent\\.com/.*")

fun String.resize(width: Int? = null, height: Int? = null): String {
  if (width == null && height == null) return this

  if (contains("i.ytimg.com")) {
    val replacement = if ((width ?: 0) >= 1200) "maxresdefault.jpg" else "hqdefault.jpg"
    return YT_IMG_SIZE.replace(this, replacement)
  }

  val large = (width ?: 0) >= 1000 || (height ?: 0) >= 1000
  val square = if (large) 1200 else 500

  if (contains("googleusercontent.com") && contains("=w")) {
    return substringBefore("=w") + "=w$square-h$square"
  }

  if (contains("yt3.ggpht.com")) {
    return substringBefore("=") + "=s${width ?: height}"
  }

  if (GOOGLE_CONTENT_HOST.matches(this)) {
    return substringBefore("=") + "=w$square-h$square"
  }

  return this
}

fun ytimgFallbackUrl(url: String): String? {
  if (!url.contains("ytimg.com")) return null
  val path = url.substringBefore('?')
  val directory = path.substringBeforeLast('/')
  return when {
    path.endsWith("/maxresdefault.jpg") -> "$directory/hq720.jpg"
    path.endsWith("/sddefault.jpg") -> "$directory/hq720.jpg"
    path.endsWith("/hq720.jpg") -> "$directory/hqdefault.jpg"
    else -> null
  }
}
