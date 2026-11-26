package com.revenge.music.playback

import com.revenge.music.db.entities.SongEntity

/** Fire-and-forget hook for pushing a like action to the remote service. */
interface ISyncUtils {
  fun likeSong(song: SongEntity)
}
