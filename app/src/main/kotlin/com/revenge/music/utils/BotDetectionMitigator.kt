package com.revenge.music.utils

/**
 * Recognises the server responses that mean "this client has been rate-limited for suspected
 * automated traffic" rather than a genuine media/authorisation failure.
 */
object BotDetectionMitigator {
    private val SIGNATURES = listOf(
        "403",
        "Error 2000",
        "Sign in to confirm",
    )

    fun isBotDetectionError(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        return SIGNATURES.any { message.contains(it, ignoreCase = true) }
    }

    fun isBotDetectionError(error: Throwable?): Boolean = isBotDetectionError(error?.message)
}
