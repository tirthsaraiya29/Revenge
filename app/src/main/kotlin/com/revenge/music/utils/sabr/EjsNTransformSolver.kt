package com.revenge.music.utils.sabr

import android.content.Context
import com.dokar.quickjs.QuickJs
import com.revenge.music.utils.cipher.CipherDeobfuscator
import com.revenge.music.utils.cipher.PlayerJsFetcher
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber

/**
 * Solves the `n` throttling parameter (and, for the cipher, `s`) using the bundled yt-dlp EJS
 * solver (`meriyah` parser + `astring` generator + `yt.solver.core.js`).
 *
 * The solver receives the whole player build, structurally extracts the requested function and
 * executes only that function, so no browser globals are required. QuickJS keeps the work off the
 * main thread and out of a WebView renderer.
 */
object EjsNTransformSolver {
  private const val TAG = "EjsNTransformSolver"
  private const val TYPE_N = "n"
  private const val TYPE_SIGNATURE = "sig"
  private const val ASSET_MERIYAH = "solver/meriyah.js"
  private const val ASSET_ASTRING = "solver/astring.js"
  private const val ASSET_SOLVER = "solver/yt.solver.core.js"

  /** Solves the `n` parameter for [n]. Returns null when no solver is available or it fails. */
  suspend fun solveN(n: String, playerJs: String? = null): String? = solve(TYPE_N, n, playerJs)

  /** Solves the `s` signature for [signature]. Returns null on failure. */
  internal suspend fun solveSignature(signature: String, playerJs: String? = null): String? =
    solve(TYPE_SIGNATURE, signature, playerJs)

  private suspend fun solve(type: String, challenge: String, playerJs: String?): String? {
    if (challenge.isEmpty()) return null
    val context = if (CipherDeobfuscator.isInitialized) CipherDeobfuscator.appContext else null
    if (context == null) {
      Timber.tag(TAG).w("CipherDeobfuscator is not initialised")
      return null
    }
    return try {
      val source = playerJs ?: PlayerJsFetcher.getPlayerJs()?.js
      if (source == null || source.length < MIN_PLAYER_LENGTH) {
        Timber.tag(TAG).w("No usable player JS for %s challenge", type)
        return null
      }
      withContext(Dispatchers.Default) { runSolver(context, source, type, challenge) }
    } catch (error: CancellationException) {
      throw error
    } catch (error: SabrException) {
      Timber.tag(TAG).w(error, "EJS solver failed for %s", type)
      null
    } catch (error: Throwable) {
      Timber.tag(TAG).w(error, "EJS solver crashed for %s", type)
      null
    }
  }

  private suspend fun runSolver(
    context: Context,
    playerJs: String,
    type: String,
    challenge: String,
  ): String? {
    val meriyah = readAsset(context, ASSET_MERIYAH)
    val astring = readAsset(context, ASSET_ASTRING)
    val solver = readAsset(context, ASSET_SOLVER)
    if (meriyah == null || astring == null || solver == null) {
      throw SabrException("EJS solver assets are missing")
    }

    val quickJs = QuickJs.create(Dispatchers.Default)
    try {
      quickJs.maxStackSize = MAX_STACK_SIZE
      quickJs.evaluate<Unit>(meriyah)
      quickJs.evaluate<Unit>(astring)
      quickJs.evaluate<Unit>(solver)
      quickJs.evaluate<Unit>("var __revengePlayer = ${JSONObject.quote(playerJs)};")
      quickJs.evaluate<Unit>("var __revengeChallenge = ${JSONObject.quote(challenge)};")
      quickJs.evaluate<Unit>("var __revengeType = ${JSONObject.quote(type)};")
      quickJs.evaluate<Unit>(
        """
        var __revengeResult = null;
        (function () {
          try {
            var output = jsc({
              type: 'player',
              player: __revengePlayer,
              requests: [{ type: __revengeType, challenges: [__revengeChallenge] }]
            });
            __revengeResult = JSON.stringify(output);
          } catch (e) {
            __revengeResult = JSON.stringify({ error: String((e && e.message) || e) });
          }
        })();
        """
          .trimIndent()
      )
      val raw = quickJs.evaluate<String>("__revengeResult")
      return extractResult(raw, challenge)
    } finally {
      runCatching { quickJs.close() }
    }
  }

  private fun extractResult(raw: String?, challenge: String): String? {
    val text = raw?.trim()
    if (text.isNullOrEmpty() || text == "null") return null
    val root = JSONObject(text)
    root.optString("error", "").takeIf { it.isNotBlank() }?.let { throw SabrException(it) }
    val responses = root.optJSONArray("responses") ?: return null
    for (index in 0 until responses.length()) {
      val response = responses.optJSONObject(index) ?: continue
      if (response.optString("type") != "result") {
        response.optString("error", "").takeIf { it.isNotBlank() }?.let { throw SabrException(it) }
        continue
      }
      val data = response.optJSONObject("data") ?: continue
      val value = data.optString(challenge, "")
      if (value.isNotEmpty()) return value
    }
    return null
  }

  private fun readAsset(context: Context, path: String): String? =
    try {
      context.assets.open(path).bufferedReader().use { it.readText() }
    } catch (error: Exception) {
      Timber.tag(TAG).w(error, "Unable to read %s", path)
      null
    }

  private const val MIN_PLAYER_LENGTH = 10_000
  private const val MAX_STACK_SIZE = 4L * 1024L * 1024L
}
