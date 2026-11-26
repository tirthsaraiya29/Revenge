package com.revenge.music.extensions

fun <T> tryOrNull(block: () -> T): T? =
  try {
    block()
  } catch (e: Exception) {
    null
  }
