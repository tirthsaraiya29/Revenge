package com.revenge.core.extensions

fun <T> tryOrNull(block: () -> T): T? =
  try {
    block()
  } catch (_: Exception) {
    null
  }
