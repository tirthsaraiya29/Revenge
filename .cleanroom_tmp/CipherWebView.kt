package com.revenge.music.utils.cipher

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener
import timber.log.Timber

/**
 * Hidden WebView used as the full-runtime fallback for evaluating player JS.
 *
 * The player is executed in a page context and the discovered cipher functions are invoked by
 * name. This path handles players whose functions depend on browser globals that the lightweight
 * QuickJS path cannot provide. A renderer crash is swallowed (`onRenderProcessGone`) so a broken
 * WebView cannot take the process down.
 */
class CipherWebView private constructor(private val webView: WebView) {

  /** Output of one evaluation. Exactly one of [signature], [n] or [error] is meaningful. */
  data class Result(
    val signature: String? = null,
    val n: String? = null,
    val error: String? = null,
  )

  /**
   * Runs [playerJs] and applies the requested transforms.
   *
   * [signatureFunction] and [nFunction] are bare function names; [signature] / [n] are the raw
   * challenge values. Any transform whose name or input is absent is skipped.
   */
  suspend fun transform(
    playerJs: String,
    signature: String? = null,
    signatureFunction: String? = null,
    n: String? = null,
    nFunction: String? = null,
  ): Result {
    val signatureName = sanitizeFunctionName(signatureFunction)
    val nName = sanitizeFunctionName(nFunction)
    if (signature != null && signatureName == null) {
      return Result(error = "Missing signature function name")
    }
    if (n != null && nName == null) {
      return Result(error = "Missing n function name")
    }
    val script = buildScript(playerJs, signature, signatureName, n, nName)
    val raw =
      withTimeoutOrNull(EVALUATION_TIMEOUT_MS) {
        withContext(Dispatchers.Main) {
          suspendCancellableCoroutine { continuation ->
            webView.evaluateJavascript(script) { value ->
              if (continuation.isActive) continuation.resume(value)
            }
          }
        }
      }
    if (raw == null) return Result(error = "Timed out evaluating player JS")
    return parse(raw)
  }

  /** Destroys the underlying WebView on the main thread. Safe to call more than once. */
  fun close() {
    val view = webView
    Handler(Looper.getMainLooper()).post {
      runCatching {
        view.stopLoading()
        view.destroy()
      }
    }
  }

  private fun buildScript(
    playerJs: String,
    signature: String?,
    signatureName: String?,
    n: String?,
    nName: String?,
  ): String {
    val signatureCall =
      if (signature != null && signatureName != null) {
        "if (typeof $signatureName === 'function') out.signature = String($signatureName(${quote(signature)}));"
      } else {
        ""
      }
    val nCall =
      if (n != null && nName != null) {
        "if (typeof $nName === 'function') out.n = String($nName(${quote(n)}));"
      } else {
        ""
      }
    return buildString {
      append("(function(){")
      append("try{")
      append(playerJs)
      append("\n;")
      append("var out={};")
      append(signatureCall)
      append(nCall)
      append("window.$RESULT_GLOBAL=JSON.stringify(out);")
      append("}catch(e){window.$RESULT_GLOBAL=JSON.stringify({error:String(e&&e.message||e)});}")
      append("})();")
    }
  }

  private fun parse(raw: String?): Result {
    if (raw == null) return Result(error = "No evaluation result")
    val inner = decode(raw) ?: return Result(error = "Unparseable evaluation result")
    return try {
      val objectValue = JSONObject(inner)
      Result(
        signature = objectValue.optString("signature", "").takeIf { it.isNotEmpty() },
        n = objectValue.optString("n", "").takeIf { it.isNotEmpty() },
        error = objectValue.optString("error", "").takeIf { it.isNotEmpty() },
      )
    } catch (error: Exception) {
      Result(error = error.message)
    }
  }

  private fun decode(raw: String): String? {
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

  companion object {
    private const val TAG = "CipherWebView"
    private const val RESULT_GLOBAL = "__revengeCipherResult"
    private const val EVALUATION_TIMEOUT_MS = 20_000L
    private val IDENTIFIER = Regex("""^[A-Za-z_$][\w$]*$""")

    private fun quote(value: String): String = JSONObject.quote(value)

    private fun sanitizeFunctionName(value: String?): String? {
      if (value == null) return null
      val name = value.substringBefore('(').trim()
      return name.takeIf { IDENTIFIER.matches(it) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun create(context: Context): CipherWebView =
      withContext(Dispatchers.Main) {
        val view = WebView(context.applicationContext)
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.allowFileAccess = false
        view.settings.allowContentAccess = false
        view.settings.blockNetworkLoads = true
        view.webViewClient =
          object : WebViewClient() {
            override fun onRenderProcessGone(
              view: WebView?,
              detail: RenderProcessGoneDetail?,
            ): Boolean {
              Timber.tag(TAG).w("Renderer process gone; destroying WebView")
              runCatching { view?.destroy() }
              return true
            }
          }
        view.loadUrl("about:blank")
        CipherWebView(view)
      }
  }
}
