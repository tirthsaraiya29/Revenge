package com.revenge.core.playback

import com.revenge.core.db.entities.SongEntity

interface ISyncUtils {
    fun likeSong(song: SongEntity)
}
