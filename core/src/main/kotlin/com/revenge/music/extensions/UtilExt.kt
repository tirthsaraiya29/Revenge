package com.revenge.music.extensions

fun <T> tryOrNull(block: () -> T): T? = try {
    block()
} catch (_: Exception) {
    null
}
