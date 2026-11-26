package com.revenge.music.playback

import com.revenge.music.db.entities.SongEntity

interface ISyncUtils {
    fun likeSong(song: SongEntity)
}
