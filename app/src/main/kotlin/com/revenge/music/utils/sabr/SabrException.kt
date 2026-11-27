package com.revenge.music.utils.sabr

/** Raised when the SABR `n`-transform solver is unavailable or cannot solve a challenge. */
class SabrException(message: String, cause: Throwable? = null) : Exception(message, cause)
