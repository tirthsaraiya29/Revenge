package com.revenge.music.utils.cipher

import android.content.Context
import com.revenge.innertube.models.YouTubeClient
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/**
 * Downloads and caches `base.js` for the current YouTube web player.
 *
 * The player id is discovered from the public `iframe_api` bootstrap script (falling back to the
 * watch page), then the build is fetched from `/s/player/<id>/player_ias.vflset/en_US/base.js`.
 * The payload is cached on disk together with its id so a failed refresh can still fall back to
 * the previous build. A build younger than [CACHE_TTL_MS] is reused without network access.
 */
object PlayerJsFetcher {
  private const val TAG = "PlayerJsFetcher"
  private const val IFRAME_API = "https://www.youtube.com/iframe_api"
  private const val WATCH_PAGE = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
  private const val PLAYER_URL_TEMPLATE =
    "https://www.youtube.com/s/player/%s/player_ias.vflset/en_US/base.js"
  private const val YOUTUBE_ORIGIN = "https://www.youtube.com"
  private const val PREFS = "cipher_player_js"
  private const val KEY_ID = "player_id"
  private const val KEY_URL = "player_url"
  private const val KEY_FETCHED_AT = "fetched_at"
  private const val CACHE_DIR = "cipher"
  private const val CACHE_FILE = "player.js"
  private const val MIN_PLAYER_JS_LENGTH = 100_000
  private const val CACHE_TTL_MS = 6L * 60L * 60L * 1000L

  /** A fetched player build. [js] is the raw `base.js` text. */
  data class PlayerJs(
    val playerId: String,
    val url: String,
    val js: String,
    val fetchedAt: Long,
  )

  private val mutex = Mutex()

  @Volatile private var memory: PlayerJs? = null

  private val client: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(20, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .writeTimeout(30, TimeUnit.SECONDS)
      .retryOnConnectionFailure(true)
      .build()
  }

  /** Returns the newest player, refreshing when [forceRefresh] is set or the cache is stale. */
  suspend fun getPlayerJs(forceRefresh: Boolean = false): PlayerJs? =
    mutex.withLock {
      val context = contextOrNull() ?: return@withLock null
      val cached = memory ?: withContext(Dispatchers.IO) { readDisk(context) }
      if (!forceRefresh && cached != null && !isStale(cached)) return@withLock cached

      val fetched = withContext(Dispatchers.IO) { fetch() }
      if (fetched != null) {
        memory = fetched
        withContext(Dispatchers.IO) { writeDisk(context, fetched) }
        fetched
      } else {
        cached
      }
    }

  fun currentPlayerId(): String? = memory?.playerId

  /** Drops the in-memory and on-disk cache; the next call performs a network fetch. */
  suspend fun invalidate() =
    mutex.withLock {
      memory = null
      val context = contextOrNull() ?: return@withLock
      withContext(Dispatchers.IO) {
        runCatching { cacheFile(context).delete() }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
      }
    }

  /** Player id from an `iframe_api` or watch-page body. Exposed for validation. */
  fun extractPlayerId(html: String): String? {
    val normalized = html.replace("\/", "/").replace("\u0026", "&")
    for (pattern in PLAYER_ID_PATTERNS) {
      pattern.find(normalized)?.groupValues?.getOrNull(1)?.let { return it }
    }
    val jsUrl = extractJsUrl(normalized) ?: return null
    return PLAYER_ID_FROM_URL.find(jsUrl)?.groupValues?.getOrNull(1)
  }

  private fun fetch(): PlayerJs? {
    val html =
      httpGet(IFRAME_API, YOUTUBE_ORIGIN + "/") ?: httpGet(WATCH_PAGE, YOUTUBE_ORIGIN + "/")
    if (html == null) {
      Timber.tag(TAG).w("Unable to download the player bootstrap")
      return null
    }
    val normalized = html.replace("\/", "/")
    val playerId = extractPlayerId(html)
    if (playerId == null) {
      Timber.tag(TAG).w("No player id in bootstrap (len=%d)", html.length)
      return null
    }
    val url =
      extractJsUrl(normalized)?.let(::absoluteUrl)
        ?: PLAYER_URL_TEMPLATE.format(playerId)
    val js = httpGet(url, YOUTUBE_ORIGIN + "/")
    if (js == null || js.length < MIN_PLAYER_JS_LENGTH) {
      Timber.tag(TAG).w("Player %s fetch failed or truncated (len=%d)", playerId, js?.length ?: 0)
      return null
    }
    Timber.tag(TAG).d("Fetched player %s (%d chars)", playerId, js.length)
    return PlayerJs(playerId = playerId, url = url, js = js, fetchedAt = System.currentTimeMillis())
  }

  private fun httpGet(url: String, referer: String): String? =
    try {
      val request =
        Request.Builder()
          .url(url)
          .header("User-Agent", YouTubeClient.USER_AGENT_WEB)
          .header("Referer", referer)
          .header("Accept-Language", "en-US,en;q=0.9")
          .get()
          .build()
      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          Timber.tag(TAG).w("HTTP %d for %s", response.code, url)
          null
        } else {
          response.body?.string()
        }
      }
    } catch (error: Exception) {
      Timber.tag(TAG).w(error, "GET %s failed", url)
      null
    }

  private fun extractJsUrl(html: String): String? =
    JS_URL.find(html)?.groupValues?.getOrNull(1)?.replace("\/", "/")

  private fun absoluteUrl(url: String): String =
    when {
      url.startsWith("http://") || url.startsWith("https://") -> url
      url.startsWith("//") -> "https:$url"
      url.startsWith("/") -> YOUTUBE_ORIGIN + url
      else -> "$YOUTUBE_ORIGIN/$url"
    }

  private fun isStale(player: PlayerJs): Boolean =
    System.currentTimeMillis() - player.fetchedAt > CACHE_TTL_MS

  private fun cacheFile(context: Context): File =
    File(File(context.filesDir, CACHE_DIR), CACHE_FILE)

  private fun readDisk(context: Context): PlayerJs? {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val id = prefs.getString(KEY_ID, null) ?: return null
    val url = prefs.getString(KEY_URL, null) ?: return null
    val fetchedAt = prefs.getLong(KEY_FETCHED_AT, 0L)
    val file = cacheFile(context)
    if (!file.isFile) return null
    return try {
      val js = file.readText()
      if (js.length < MIN_PLAYER_JS_LENGTH) return null
      PlayerJs(id, url, js, fetchedAt).also { memory = it }
    } catch (error: Exception) {
      Timber.tag(TAG).w(error, "Unable to read cached player")
      null
    }
  }

  private fun writeDisk(context: Context, player: PlayerJs) {
    try {
      val dir = File(context.filesDir, CACHE_DIR)
      if (!dir.isDirectory && !dir.mkdirs()) {
        Timber.tag(TAG).w("Unable to create cipher cache directory")
        return
      }
      cacheFile(context).writeText(player.js)
      context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_ID, player.playerId)
        .putString(KEY_URL, player.url)
        .putLong(KEY_FETCHED_AT, player.fetchedAt)
        .apply()
    } catch (error: Exception) {
      Timber.tag(TAG).w(error, "Unable to cache player")
    }
  }

  private fun contextOrNull(): Context? =
    if (CipherDeobfuscator.isInitialized) CipherDeobfuscator.appContext else null

  private val PLAYER_ID_PATTERNS =
    listOf(
      Regex("""/s/player/([0-9A-Za-z_-]{8})/"""),
      Regex("""/player/([0-9A-Za-z_-]{8})/"""),
    )

  private val PLAYER_ID_FROM_URL = Regex("""/s/player/([0-9A-Za-z_-]{8})/""")

  private val JS_URL = Regex("""["']jsUrl["']\s*:\s*["']([^"']+)["']""")
}
