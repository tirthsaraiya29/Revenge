package com.revenge.core.db

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): LocalDateTime? =
        value?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDateTime() }

    @TypeConverter
    fun dateToTimestamp(date: LocalDateTime?): Long? =
        date?.toInstant(ZoneOffset.UTC)?.toEpochMilli()
}
