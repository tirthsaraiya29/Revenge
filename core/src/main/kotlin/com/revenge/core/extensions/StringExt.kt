package com.revenge.core.extensions

import androidx.sqlite.db.SimpleSQLiteQuery
import java.net.InetSocketAddress

inline fun <reified T : Enum<T>> String?.toEnum(defaultValue: T): T {
  if (this == null) return defaultValue
  return try {
    enumValueOf<T>(this)
  } catch (_: IllegalArgumentException) {
    defaultValue
  }
}

fun String.toSQLiteQuery(): SimpleSQLiteQuery = SimpleSQLiteQuery(this)

fun String.toInetSocketAddress(): InetSocketAddress {
  val parts = split(":")
  return InetSocketAddress.createUnresolved(parts[0], parts[1].toInt())
}
