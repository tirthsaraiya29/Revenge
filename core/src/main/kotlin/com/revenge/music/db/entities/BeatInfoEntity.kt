package com.revenge.music.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "beat_info")
data class BeatInfoEntity(
  @PrimaryKey val songId: String,
  val bpm: Float,
  val firstBeatOffsetMs: Long,
  val confidence: Float,
  val analyzedAt: Long = System.currentTimeMillis(),
  /** First downbeat after the intro; where a mix-in should start. */
  val mixInPointMs: Long? = null,
  /** Point where the outgoing track's body ends and the transition begins. */
  val mixOutPointMs: Long? = null,
  /** 0 = C, 1 = C#, ... 11 = B. Null when the chroma signal was too weak to classify. */
  val keyPitchClass: Int? = null,
  val keyIsMinor: Boolean? = null,
)
