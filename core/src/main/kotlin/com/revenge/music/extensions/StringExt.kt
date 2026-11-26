package com.revenge.music.extensions

import androidx.sqlite.db.SimpleSQLiteQuery
import java.net.InetSocketAddress
import java.net.InetSocketAddress.createUnresolved

inline fun <reified T : Enum<T>> String?.toEnum(defaultValue: T): T {
  val name = this ?: return defaultValue
  return try {
    enumValueOf(name)
  } catch (e: IllegalArgumentException) {
    defaultValue
  }
}

fun String.toSQLiteQuery(): SimpleSQLiteQuery = SimpleSQLiteQuery(this)

/** Parses "host:port" without performing DNS resolution. */
fun String.toInetSocketAddress(): InetSocketAddress {
  val (host, port) = split(":")
  return createUnresolved(host, port.toInt())
}
