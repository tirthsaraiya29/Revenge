package com.revenge.music.utils.potoken

import android.content.Context
import android.util.Base64
import android.webkit.WebView
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONTokener
import timber.log.Timber

/**
 * Small helpers shared by the hidden WebViews: injecting string literals safely, awaiting
 * `evaluateJavascript`, reading bundled assets and moving binary token data across the JS
 * boundary as base64.
 */
object JavaScriptUtil {
  private const val TAG = "JavaScriptUtil"

  /** JavaScript string literal for [value] (org.json escaping is valid JS). */
  fun quote(value: String): String = org.json.JSONObject.quote(value)

  /** Decodes the JSON value returned by `evaluateJavascript`; null/undefined become null. */
  fun decode(raw: String?): String? {
    if (raw == null) return null
    val value = raw.trim()
    if (value.isEmpty() || value == "null" || value == "undefined") return null
    return try {
      when (val parsed = JSONTokener(value).nextValue()) {
        is String -> parsed
        null -> null
        else -> parsed.toString()
      }
    } catch (error: Exception) {
      null
    }
  }

  /** Evaluates [script] on the main thread and returns the raw callback value. */
  suspend fun evaluate(webView: WebView, script: String): String? =
    withContext(Dispatchers.Main) {
      suspendCancellableCoroutine { continuation ->
        webView.evaluateJavascript(script) { value ->
          if (continuation.isActive) continuation.resume(value)
        }
      }
    }

  /** Reads a bundled asset, returning null when it is missing. */
  fun readAsset(context: Context, path: String): String? =
    try {
      context.assets.open(path).bufferedReader().use { it.readText() }
    } catch (error: Exception) {
      Timber.tag(TAG).w(error, "Unable to read asset %s", path)
      null
    }

  /** Renders [bytes] as a JS `Uint8Array` literal. */
  fun toJsUint8Array(bytes: ByteArray): String =
    "new Uint8Array([" + bytes.joinToString(",") { (it.toInt() and 0xFF).toString() } + "])"

  /** Parses a JSON array of byte values emitted by the WebView. */
  fun jsonArrayToBytes(raw: String?): ByteArray? {
    val text = decode(raw) ?: return null
    return try {
      val array = JSONArray(text)
      ByteArray(array.length()) { index -> (array.optInt(index, 0) and 0xFF).toByte() }
    } catch (error: Exception) {
      null
    }
  }

  fun toBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

  fun fromBase64(base64: String): ByteArray? =
    try {
      Base64.decode(base64, Base64.DEFAULT)
    } catch (error: Exception) {
      null
    }

  /** JS helpers: base64-encode a `Uint8Array` and UTF-8 encode a string without Kotlin help. */
  const val JS_BASE64_HELPER =
    """
    function __revengeToBase64(bytes) {
      var binary = '';
      for (var i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
      return btoa(binary);
    }
    function __revengeUtf8Bytes(text) {
      if (typeof TextEncoder !== 'undefined') return new TextEncoder().encode(text);
      var escaped = unescape(encodeURIComponent(text));
      var bytes = new Uint8Array(escaped.length);
      for (var i = 0; i < escaped.length; i++) bytes[i] = escaped.charCodeAt(i);
      return bytes;
    }
    """
}
