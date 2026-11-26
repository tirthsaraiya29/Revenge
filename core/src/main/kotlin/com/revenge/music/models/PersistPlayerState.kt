package com.revenge.music.models

import java.io.Serializable

/** Snapshot of the player's transport state, written to disk on shutdown. */
data class PersistPlayerState(
  val playWhenReady: Boolean,
  val repeatMode: Int,
  val shuffleModeEnabled: Boolean,
  val volume: Float,
  val currentPosition: Long,
  val currentMediaItemIndex: Int,
  val playbackState: Int,
  val timestamp: Long = System.currentTimeMillis(),
) : Serializable
