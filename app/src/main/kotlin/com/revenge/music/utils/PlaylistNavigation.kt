package com.revenge.music.utils

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

private const val ONLINE_PLAYLIST_ROUTE = "online_playlist/"
private const val LOCAL_PLAYLIST_ROUTE = "local_playlist/"

fun validOnlinePlaylistId(playlistId: String?): String? {
    if (playlistId == null) return null
    val trimmed = playlistId.trim()
    if (trimmed.isEmpty() || trimmed == "null") return null
    return playlistId
}

fun onlinePlaylistIdFromUrl(url: String): String? {
    val rawQuery = try {
        URI(url.trim()).rawQuery
    } catch (_: Exception) {
        return null
    } ?: return null

    val rawId = rawQuery.split('&')
        .firstOrNull { it.substringBefore('=') == "list" }
        ?.substringAfter('=', "")
        ?: return null

    if (rawId.isEmpty()) return null
    val decoded = try {
        URLDecoder.decode(rawId, Charsets.UTF_8.name())
    } catch (_: Exception) {
        return null
    }
    return validOnlinePlaylistId(decoded)
}

fun onlinePlaylistRoute(playlistId: String?): String? {
    val id = validOnlinePlaylistId(playlistId) ?: return null
    return ONLINE_PLAYLIST_ROUTE + encode(id)
}

fun libraryPlaylistRoute(
    localPlaylistId: String,
    browseId: String?,
    isEditable: Boolean,
    songCount: Int,
    remoteSongCount: Int?,
): String {
    val onlineId = validOnlinePlaylistId(browseId)
    if (!isEditable && songCount == 0 && remoteSongCount != 0 && onlineId != null) {
        return ONLINE_PLAYLIST_ROUTE + encode(onlineId)
    }
    return LOCAL_PLAYLIST_ROUTE + encode(localPlaylistId)
}

private fun encode(value: String): String =
    URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
