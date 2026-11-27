package com.revenge.music.utils.potoken

import com.revenge.music.utils.cipher.CipherDeobfuscator
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Lazily owns the BotGuard WebView and mints web-client PO tokens on demand.
 *
 * [initialize] is called once during app start-up (main thread) and only flips a flag; the
 * WebView is created on the first mint, on the main thread, and reused for later requests. A
 * failed renderer is discarded so the next call starts from a fresh page.
 */
class PoTokenGenerator {
  private companion object {
    private const val TAG = "PoTokenGenerator"
  }

  private val mutex = Mutex()

  @Volatile private var initialized = false

  @Volatile private var webView: PoTokenWebView? = null

  /** Marks the generator usable. Does no I/O and never blocks the caller. */
  fun initialize() {
    initialized = true
  }

  /**
   * Mints tokens for [videoId] bound to [visitorData]. Returns null when the generator is not
   * initialised, BotGuard is unavailable, or minting fails.
   */
  suspend fun getWebClientPoToken(videoId: String, visitorData: String): PoTokenResult? {
    if (!initialized) {
      Timber.tag(TAG).w("getWebClientPoToken before initialize()")
      return null
    }
    if (!CipherDeobfuscator.isInitialized) {
      Timber.tag(TAG).w("CipherDeobfuscator context is not available")
      return null
    }
    return mutex.withLock {
      try {
        val generator = webView ?: createGenerator().also { webView = it }
        generator.generateTokens(videoId, visitorData)
      } catch (error: CancellationException) {
        throw error
      } catch (error: PoTokenException) {
        Timber.tag(TAG).w(error, "PO token generation failed")
        reset()
        null
      } catch (error: Throwable) {
        Timber.tag(TAG).w(error, "PO token generation crashed")
        reset()
        null
      }
    }
  }

  private suspend fun createGenerator(): PoTokenWebView =
    PoTokenWebView.getNewPoTokenGenerator(CipherDeobfuscator.appContext)

  private fun reset() {
    webView?.close()
    webView = null
  }
}
