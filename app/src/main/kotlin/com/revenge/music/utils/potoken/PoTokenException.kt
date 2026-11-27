package com.revenge.music.utils.potoken

/** Raised when a PO token cannot be minted or the BotGuard runtime fails. */
class PoTokenException(message: String, cause: Throwable? = null) : Exception(message, cause)
