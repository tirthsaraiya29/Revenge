package com.revenge.music.db.entities

/** Common shape for anything the local library UI can browse. */
sealed class LocalItem {
  abstract val id: String
  abstract val title: String
  abstract val thumbnailUrl: String?
}
