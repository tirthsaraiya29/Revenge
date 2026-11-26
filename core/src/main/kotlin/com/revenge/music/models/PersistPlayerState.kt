package com.revenge.music.models

import java.io.Serializable

/** Serializable snapshot of transport state used to resume playback after process death. */
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
