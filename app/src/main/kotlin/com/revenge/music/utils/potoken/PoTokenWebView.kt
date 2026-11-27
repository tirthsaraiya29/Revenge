package com.revenge.music.utils.potoken

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import com.revenge.innertube.models.YouTubeClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * Hidden WebView runtime for BotGuard PO tokens.
 *
 * The bundled `po_token.html` implements the BotGuard client (interpreter execution, snapshot and
 * minter creation). This class loads that page, fetch/executes the remote challenge, exchanges the
 * snapshot for an integrity token and mints the two tokens the web client needs. All calls cross
 * the JS boundary through a narrow [Bridge]; results are correlated by label.
 *
 * Renders are disposable: [close] tears the WebView down, and a renderer crash merely fails the
 * pending request instead of the process.
 */
class PoTokenWebView private constructor(
  private val webView: WebView,
  private val callbacks: ConcurrentHashMap<String, CancellableContinuation<String>>,
) {
  private val mainHandler = Handler(Looper.getMainLooper())
  private val http: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(20, TimeUnit.SECONDS)
      .readTimeout(20, TimeUnit.SECONDS)
      .writeTimeout(20, TimeUnit.SECONDS)
      .retryOnConnectionFailure(true)
      .build()
  }

  /** Mints the player-request and streaming-data tokens bound to [visitorData] / [videoId]. */
  suspend fun generateTokens(videoId: String, visitorData: String): PoTokenResult? {
    val challenge = fetchChallenge(videoId, visitorData) ?: return null
    val run =
      awaitJs(
        "run",
        """
        (async function () {
          var result = await runBotGuard(${challenge.toString()});
          window.__revengeSignal = result.webPoSignalOutput;
          return JSON.stringify({ botguardResponse: result.botguardResponse });
        })()
        """
          .trimIndent(),
      ) ?: return null
    val botguardResponse =
      runCatching { JSONObject(run).optString("botguardResponse") }.getOrNull()
        ?.takeIf { it.isNotBlank() } ?: return null

    val integrityBytes = fetchIntegrity(botguardResponse, visitorData) ?: return null
    val minterReady =
      awaitJs(
        "minter",
        """
        (async function () {
          await createPoTokenMinter(
            window.__revengeSignal,
            ${JavaScriptUtil.toJsUint8Array(integrityBytes)}
          );
          return 'ok';
        })()
        """
          .trimIndent(),
      )
    if (minterReady == null) return null

    val playerToken = mintToken(visitorData) ?: return null
    val streamingToken = mintToken(videoId)
    return PoTokenResult(
      playerRequestPoToken = playerToken,
      streamingDataPoToken = streamingToken ?: playerToken,
      visitorData = visitorData,
    )
  }

  /** Fails pending calls and destroys the WebView on the main thread. */
  fun close() {
    val view = webView
    for (label in callbacks.keys.toList()) {
      callbacks.remove(label)?.let { continuation ->
        if (continuation.isActive) continuation.resume(ERROR_PREFIX + "closed")
      }
    }
    mainHandler.post {
      runCatching {
        view.removeJavascriptInterface(BRIDGE_NAME)
        view.stopLoading()
        view.destroy()
      }
    }
  }

  private suspend fun awaitJs(label: String, expression: String): String? {
    val quotedLabel = JavaScriptUtil.quote(label)
    val raw =
      withTimeoutOrNull(JS_TIMEOUT_MS) {
        suspendCancellableCoroutine<String> { continuation ->
          callbacks[label] = continuation
          continuation.invokeOnCancellation { callbacks.remove(label) }
          mainHandler.post {
            val script =
              """
              (function () {
                function fail(e) {
                  window.$BRIDGE_NAME.onError($quotedLabel, String((e && e.message) || e));
                }
                try {
                  Promise.resolve((async function () { return ($expression); })())
                    .then(function (r) {
                      window.$BRIDGE_NAME.onResult(
                        $quotedLabel,
                        typeof r === 'string' ? r : JSON.stringify(r)
                      );
                    })
                    .catch(fail);
                } catch (e) {
                  fail(e);
                }
              })();
              """
                .trimIndent()
            webView.evaluateJavascript(script, null)
          }
        }
      }
    callbacks.remove(label)
    if (raw == null) return null
    if (raw.startsWith(ERROR_PREFIX)) {
      Timber.tag(TAG).w("JS %s failed: %s", label, raw.removePrefix(ERROR_PREFIX))
      return null
    }
    return raw
  }

  private suspend fun mintToken(identifier: String): String? =
    awaitJs(
      "mint-" + identifier.hashCode(),
      """
      (async function () {
        var bytes = await obtainPoToken(__revengeUtf8Bytes(${JavaScriptUtil.quote(identifier)}));
        return __revengeToBase64(bytes);
      })()
      """
        .trimIndent(),
    )

  private fun fetchChallenge(videoId: String, visitorData: String): JSONObject? {
    val client =
      JSONObject()
        .put("clientName", "WEB")
        .put("clientVersion", "2.20260213.01.00")
        .put("hl", "en")
        .put("gl", "US")
        .put("visitorData", visitorData)
    val body =
      JSONObject()
        .put("context", JSONObject().put("client", client))
        .put("videoId", videoId)
        .put("identifier", visitorData)
    return postJson(CHALLENGE_URL, body)
  }

  private fun fetchIntegrity(botguardResponse: String, visitorData: String): ByteArray? {
    val body =
      JSONObject()
        .put("botguardResponse", botguardResponse)
        .put("identifier", visitorData)
    val response = postJson(INTEGRITY_URL, body) ?: return null
    val value = response.opt("integrityToken") ?: response.opt("token") ?: return null
    return when (value) {
      is String -> JavaScriptUtil.fromBase64(value)
      is JSONArray -> ByteArray(value.length()) { index ->
        (value.optInt(index, 0) and 0xFF).toByte()
      }
      else -> null
    }
  }

  private fun postJson(url: String, body: JSONObject): JSONObject? {
    return try {
      val request =
        Request.Builder()
          .url(url)
          .header("User-Agent", YouTubeClient.USER_AGENT_WEB)
          .header("Content-Type", "application/json")
          .header("Origin", YOUTUBE_ORIGIN)
          .header("Accept", "application/json")
          .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
          .build()
      http.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          Timber.tag(TAG).w("HTTP %d for %s", response.code, url)
          null
        } else {
          val text = response.body?.string()
          if (text.isNullOrBlank()) null else runCatching { JSONObject(text) }.getOrNull()
        }
      }
    } catch (error: Exception) {
      Timber.tag(TAG).w(error, "POST %s failed", url)
      null
    }
  }

  /** Narrow JS-to-Kotlin bridge. Only these three calls are reachable from the page. */
  private class Bridge(
    private val resultSink: (String, String) -> Unit,
    private val errorSink: (String, String) -> Unit,
    private val logSink: (String) -> Unit,
  ) {
    @JavascriptInterface
    fun onResult(label: String, value: String) = resultSink(label, value)

    @JavascriptInterface
    fun onError(label: String, message: String) = errorSink(label, message)

    @JavascriptInterface
    fun onLog(message: String) = logSink(message)
  }

  companion object {
    private const val TAG = "PoTokenWebView"
    private const val BRIDGE_NAME = "RevengeBridge"
    private const val ERROR_PREFIX = "__REVENGE_ERROR__:"
    private const val HTML_ASSET = "po_token.html"
    private const val YOUTUBE_ORIGIN = "https://www.youtube.com"
    private const val INNERTUBE_API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private const val CHALLENGE_URL =
      "$YOUTUBE_ORIGIN/api/jnn/_/v1/challenge?key=$INNERTUBE_API_KEY"
    private const val INTEGRITY_URL =
      "$YOUTUBE_ORIGIN/api/jnn/_/v1/integrity?key=$INNERTUBE_API_KEY"
    private const val LOAD_TIMEOUT_MS = 15_000L
    private const val JS_TIMEOUT_MS = 25_000L
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * Builds a hidden WebView, loads the bundled BotGuard client and waits until its script has
     * run. Must be called from a coroutine; creation itself happens on the main thread.
     */
    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    suspend fun getNewPoTokenGenerator(context: Context): PoTokenWebView {
      val html = JavaScriptUtil.readAsset(context, HTML_ASSET)
        ?: throw PoTokenException("Missing $HTML_ASSET")
      val callbacks = ConcurrentHashMap<String, CancellableContinuation<String>>()
      val bridge =
        Bridge(
          resultSink = { label, value ->
            callbacks.remove(label)?.let { if (it.isActive) it.resume(value) }
          },
          errorSink = { label, message ->
            callbacks.remove(label)?.let { if (it.isActive) it.resume(ERROR_PREFIX + message) }
          },
          logSink = { message -> Timber.tag(TAG).d("%s", message) },
        )
      val ready = CompletableDeferred<Unit>()
      val view =
        withContext(Dispatchers.Main) {
          val webView = WebView(context.applicationContext)
          webView.settings.javaScriptEnabled = true
          webView.settings.domStorageEnabled = true
          webView.settings.allowFileAccess = false
          webView.settings.allowContentAccess = false
          webView.settings.blockNetworkLoads = false
          webView.addJavascriptInterface(bridge, BRIDGE_NAME)
          webView.webViewClient =
            object : WebViewClient() {
              override fun onPageFinished(view: WebView?, url: String?) {
                ready.complete(Unit)
              }

              override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?,
              ): Boolean {
                ready.completeExceptionally(PoTokenException("Renderer process gone"))
                runCatching { view?.destroy() }
                return true
              }
            }
          webView.loadDataWithBaseURL(YOUTUBE_ORIGIN, html, "text/html", "UTF-8", null)
          webView
        }
      val instance = PoTokenWebView(view, callbacks)
      val loaded = withTimeoutOrNull(LOAD_TIMEOUT_MS) { runCatching { ready.await() }.isSuccess }
      if (loaded != true) {
        instance.close()
        throw PoTokenException("Timed out loading $HTML_ASSET")
      }
      JavaScriptUtil.evaluate(view, JavaScriptUtil.JS_BASE64_HELPER)
      return instance
    }
  }
}
