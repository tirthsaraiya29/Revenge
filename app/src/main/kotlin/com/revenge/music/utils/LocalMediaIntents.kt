package com.revenge.music.utils

import android.content.Context
import android.content.Intent
import android.net.Uri

fun String.isLocalMediaId(): Boolean =
  startsWith("content://") && (contains("media/external") || contains("com.android.providers"))

fun shareLocalAudio(context: Context, uri: String, title: String? = null) {
  runCatching {
    val intent =
      Intent(Intent.ACTION_SEND).apply {
        type = "audio/*"
        putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
        title?.let { putExtra(Intent.EXTRA_TITLE, it) }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    context.startActivity(
      Intent.createChooser(intent, title ?: "Share audio").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
  }
}
