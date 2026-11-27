package com.revenge.music.utils.cipher

import android.content.Context
import com.revenge.music.utils.sabr.EjsNTransformSolver
import java.net.URLDecoder
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Turns a `signatureCipher` (or a direct but throttled URL) into a playable media URL.
 *
 * The cipher is `s=<cipher>&sp=<param>&url=<encoded url>`: [deobfuscateStreamUrl] removes the `s`
 * obscuring via the player's signature routine, appends the result under `sp`, and then rewrites
 * the player's `n` throttling parameter. Player JS is fetched and cached by [PlayerJsFetcher];
 * parsed recipes are cached by [PlayerConfigStore]. Signature and `n` are solved with the bundled
 * EJS/QuickJS solver first, with a hidden-WebView fallback for players that need browser globals.
 */
object CipherDeobfuscator {
  private const val TAG = "CipherDeobfuscator"
  private const val PARAM_SIGNATURE = "s"
  private const val PARAM_SIGNATURE_PARAMETER = "sp"
  private const val PARAM_URL = "url"
  private const val PARAM_N = "n"

  @Volatile lateinit var appContext: Context
    private set

  /** True once [initialize] has stored the application context. */
  val isInitialized: Boolean
    get() = this::appContext.isInitialized

  private val recovery = RendererRecoveryPolicy.forPlayerRenderer()
  private val transformMutex = Mutex()

  /** Stores the application context and loads the bundled player snapshots. */
  fun initialize(context: Context) {
    appContext = context.applicationContext
    PlayerConfigStore.initialize(appContext)
    PlayerDatesStore.initialize(appContext)
  }

  /**
   * Deobfuscates [signatureCipher] for [videoId] and returns the final URL, or null when the
   * cipher is incomplete or no transform could be applied.
   */
  suspend fun deobfuscateStreamUrl(signatureCipher: String, videoId: String): String? =
    transformMutex.withLock {
      if (!isInitialized) {
        Timber.tag(TAG).w("deobfuscateStreamUrl called before initialise()")
        return@withLock null
      }

      val cipher =
        if (signatureCipher.startsWith("http")) emptyMap() else parseQuery(signatureCipher)
      val url = cipher[PARAM_URL] ?: signatureCipher.takeIf { it.startsWith("http") }
      if (url.isNullOrBlank()) {
        Timber.tag(TAG).w("Cipher for %s has no url", videoId)
        return@withLock null
      }

      val signature = cipher[PARAM_SIGNATURE]
      val signatureParameter = cipher[PARAM_SIGNATURE_PARAMETER]
      val needsSignature = !signature.isNullOrEmpty()
      if (needsSignature && signatureParameter.isNullOrEmpty()) {
        Timber.tag(TAG).w("Cipher for %s has s but no sp", videoId)
        return@withLock null
      }
      val n = queryParameter(url, PARAM_N)

      val player =
        if (needsSignature || n != null) {
          PlayerJsFetcher.getPlayerJs(forceRefresh = !recovery.shouldRetry())
        } else {
          null
        }
      if (needsSignature && player == null) {
        recovery.recordFailure()
        Timber.tag(TAG).w("No player JS available for %s", videoId)
        return@withLock null
      }

      var result = url
      if (needsSignature) {
        val entry = configFor(player!!)
        val deobfuscated = decipher(signature!!, player, entry)
        if (deobfuscated == null) {
          recovery.recordFailure()
          Timber.tag(TAG).w("Signature deobfuscation failed for %s", videoId)
          return@withLock null
        }
        result = appendParameter(result, signatureParameter!!, deobfuscated)
      }
      if (n != null && player != null) {
        nTransform(n, player, configFor(player))?.let { transformed ->
          result = replaceParameter(result, PARAM_N, transformed)
        }
      }
      recovery.recordSuccess()
      result
    }

  private suspend fun configFor(player: PlayerJsFetcher.PlayerJs): PlayerConfigStore.Entry =
    withContext(Dispatchers.Default) {
      PlayerConfigStore.find(player.playerId)?.let { return@withContext it }
      val parsed = PlayerConfigParser.parse(player.playerId, player.js)
      if (parsed != null) PlayerConfigStore.save(player.playerId, parsed)
      parsed ?: PlayerConfigStore.Entry()
    }

  private suspend fun decipher(
    signature: String,
    player: PlayerJsFetcher.PlayerJs,
    entry: PlayerConfigStore.Entry,
  ): String? {
    EjsNTransformSolver.solveSignature(signature, player.js)?.let { return it }

    val spec =
      entry.sig
        ?: FunctionNameExtractor.findSignatureFunction(player.js)?.name
        ?: return null
    val view = CipherWebView.create(appContext)
    return try {
      val result = view.transform(player.js, signature = signature, signatureFunction = spec)
      result.error?.let { Timber.tag(TAG).w("WebView signature error: %s", it) }
      result.signature
    } finally {
      view.close()
    }
  }

  private suspend fun nTransform(
    n: String,
    player: PlayerJsFetcher.PlayerJs,
    entry: PlayerConfigStore.Entry,
  ): String? {
    EjsNTransformSolver.solveN(n, player.js)?.let { return it }

    val spec =
      entry.nClass
        ?: FunctionNameExtractor.findNFunction(player.js)?.name
        ?: return null
    val view = CipherWebView.create(appContext)
    return try {
      val result = view.transform(player.js, n = n, nFunction = spec)
      result.error?.let { Timber.tag(TAG).w("WebView n-transform error: %s", it) }
      result.n
    } finally {
      view.close()
    }
  }

  private fun parseQuery(query: String): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    for (part in query.split('&')) {
      if (part.isEmpty()) continue
      val separator = part.indexOf('=')
      if (separator <= 0) continue
      result[decode(part.substring(0, separator))] = decode(part.substring(separator + 1))
    }
    return result
  }

  private fun queryParameter(url: String, key: String): String? {
    val query = url.substringAfter('?', "").substringBefore('#')
    if (query.isEmpty()) return null
    return parseQuery(query)[key]
  }

  private fun appendParameter(url: String, key: String, value: String): String {
    val fragment = url.substringAfter('#', "")
    val base = if ('#' in url) url.substringBefore('#') else url
    val separator = if ('?' in base) '&' else '?'
    val appended = "$base$separator${encode(key)}=${encode(value)}"
    return if (fragment.isEmpty()) appended else "$appended#$fragment"
  }

  private fun replaceParameter(url: String, key: String, value: String): String {
    val fragment = url.substringAfter('#', "")
    val base = if ('#' in url) url.substringBefore('#') else url
    val prefix = base.substringBefore('?')
    val query = base.substringAfter('?', "")
    val kept =
      if (query.isEmpty()) {
        emptyList()
      } else {
        query.split('&').filter { part ->
          part.isNotEmpty() && decode(part.substringBefore('=')) != key
        }
      }
    val newQuery = (kept + "${encode(key)}=${encode(value)}").joinToString("&")
    val rebuilt = if (newQuery.isEmpty()) prefix else "$prefix?$newQuery"
    return if (fragment.isEmpty()) rebuilt else "$rebuilt#$fragment"
  }

  private fun decode(value: String): String =
    try {
      URLDecoder.decode(value, Charsets.UTF_8.name())
    } catch (error: Exception) {
      value
    }

  private fun encode(value: String): String =
    try {
      URLEncoder.encode(value, Charsets.UTF_8.name())
    } catch (error: Exception) {
      value
    }
}
