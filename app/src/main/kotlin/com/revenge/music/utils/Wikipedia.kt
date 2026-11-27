package com.revenge.music.utils

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class WikipediaThumbnail(val source: String?)

data class WikipediaSummary(
    val extract: String?,
    val title: String?,
    val thumbnail: WikipediaThumbnail?,
)

/** Best-effort English Wikipedia lookups; every failure resolves to `null`. */
object Wikipedia {
    private const val HOST = "https://en.wikipedia.org"
    private const val TIMEOUT_MS = 10_000

    suspend fun fetchArtistBio(name: String): WikipediaSummary? = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return@withContext null
        summaryFor(cleanName) ?: firstSearchResult(cleanName)?.let { summaryFor(it) }
    }

    suspend fun fetchSongStory(title: String, artist: String, album: String?): WikipediaSummary? =
        withContext(Dispatchers.IO) {
            val query = listOfNotNull(
                title.trim().takeIf { it.isNotEmpty() },
                artist.trim().takeIf { it.isNotEmpty() },
                album?.trim()?.takeIf { it.isNotEmpty() },
            ).joinToString(" ")
            if (query.isEmpty()) return@withContext null
            firstSearchResult(query)?.let { summaryFor(it) }
        }

    private fun firstSearchResult(query: String): String? {
        val url = "$HOST/w/api.php?action=query&format=json&list=search&srlimit=1&srsearch=" +
            URLEncoder.encode(query, Charsets.UTF_8.name())
        val json = request(url) ?: return null
        return try {
            JSONObject(json)
                .getJSONObject("query")
                .getJSONArray("search")
                .optJSONObject(0)
                ?.optString("title")
                ?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun summaryFor(title: String): WikipediaSummary? {
        val url = "$HOST/api/rest_v1/page/summary/" + URLEncoder.encode(title, Charsets.UTF_8.name())
        val json = request(url) ?: return null
        return try {
            val root = JSONObject(json)
            val extract = root.optString("extract").takeIf { it.isNotBlank() }
            val summaryTitle = root.optString("title").takeIf { it.isNotBlank() }
            if (extract == null && summaryTitle == null) return null
            val thumbnail = root.optJSONObject("thumbnail")
                ?.optString("source")
                ?.takeIf { it.isNotBlank() }
            WikipediaSummary(
                extract = extract,
                title = summaryTitle,
                thumbnail = thumbnail?.let(::WikipediaThumbnail),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun request(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "RevengeMusic/1.0")
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
