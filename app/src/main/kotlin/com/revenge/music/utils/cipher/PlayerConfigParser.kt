package com.revenge.music.utils.cipher

import timber.log.Timber

/**
 * Derives a [PlayerConfigStore.Entry] from a downloaded player JS build.
 *
 * This is the runtime fallback for player ids missing from the bundled
 * `player_configs.json`: it locates the signature and `n` routines and records the exact
 * signature call site so later resolutions do not have to re-parse the (very large) player.
 */
object PlayerConfigParser {
  private const val TAG = "PlayerConfigParser"
  private const val MIN_PLAYER_JS_LENGTH = 10_000

  /**
   * Parses [playerJs]. Returns null for input too small to be a real player or when neither a
   * signature nor an `n` routine can be identified.
   */
  fun parse(playerId: String, playerJs: String): PlayerConfigStore.Entry? {
    if (playerJs.length < MIN_PLAYER_JS_LENGTH) return null

    val signatureFunction = FunctionNameExtractor.findSignatureFunction(playerJs)
    val nFunction = FunctionNameExtractor.findNFunction(playerJs, signatureFunction)
    if (signatureFunction?.name == null && nFunction?.name == null) {
      Timber.tag(TAG).w("Player %s: no cipher routines found", playerId)
      return null
    }

    val sig =
      signatureFunction?.name?.let { name ->
        FunctionNameExtractor.findSignatureCallDescriptor(playerJs, name)
      }
    return PlayerConfigStore.Entry(
      sig = sig,
      nClass = nFunction?.name,
      sts = FunctionNameExtractor.findSignatureTimestamp(playerJs),
    )
  }
}
