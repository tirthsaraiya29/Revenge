package com.revenge.music.db.entities

import androidx.room.Entity

@Entity(tableName = "playCount", primaryKeys = ["song", "year", "month"])
class PlayCountEntity(
    val song: String,
    val year: Int = -1,
    val month: Int = -1,
    val count: Int = -1,
)
