package com.revenge.music.db

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Room stores timestamps as UTC epoch milliseconds. */
class Converters {
  @TypeConverter
  fun fromTimestamp(value: Long?): LocalDateTime? =
    value?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC) }

  @TypeConverter
  fun dateToTimestamp(date: LocalDateTime?): Long? =
    date?.atZone(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
}
