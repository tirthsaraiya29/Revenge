package com.revenge.music.localmedia

import java.util.Locale

object SupportedLocalAudio {
    val supportedExtensions: Set<String> = setOf(
        "aac", "amr", "flac", "m4a", "m4b", "m4p", "mid", "mka", "mp3", "mp4",
        "oga", "ogg", "opus", "wav", "weba", "webm", "3ga", "3gp",
    )

    val blockedExtensions: Set<String> = setOf(
        "ape", "cue", "dsd", "dsf", "dff", "midi", "iso", "tta", "wv", "wvc",
    )

    val supportedMimeTypes: Set<String> = setOf(
        "audio/aac", "audio/aac-adts", "audio/amr", "audio/amr-wb", "audio/flac",
        "audio/mp4", "audio/mpeg", "audio/ogg", "audio/opus", "audio/wav", "audio/wave",
        "audio/webm", "audio/x-aac", "audio/x-flac", "audio/x-m4a", "audio/x-mka",
        "audio/x-matroska", "audio/x-mpeg", "audio/x-wav", "application/ogg",
        "video/mp4", "video/webm", "video/3gpp",
    )

    fun isSupported(displayName: String?, mimeType: String?): Boolean {
        val extension = extensionOf(displayName)
        if (extension.isNotEmpty() && extension in blockedExtensions) return false
        if (extension.isNotEmpty() && extension in supportedExtensions) return true
        return normalizedMime(mimeType) in supportedMimeTypes
    }

    fun isSupportedMimeType(mimeType: String?): Boolean {
        val normalized = normalizedMime(mimeType)
        if (normalized.isEmpty()) return false
        return normalized in supportedMimeTypes || normalized.startsWith("audio/")
    }

    fun extensionOf(displayName: String?): String {
        val name = displayName?.trim().orEmpty()
        if (name.isEmpty()) return ""
        val dot = name.lastIndexOf('.')
        if (dot < 0 || dot == name.lastIndex) return ""
        return name.substring(dot + 1).lowercase(Locale.ROOT)
    }

    fun normalizedMime(mimeType: String?): String =
        mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT).orEmpty()
}
