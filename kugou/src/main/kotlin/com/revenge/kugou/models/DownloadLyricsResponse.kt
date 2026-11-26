package com.revenge.kugou.models

import kotlinx.serialization.Serializable

/** Payload returned by the KuGou lyric download endpoint; [content] is base64 encoded. */
@Serializable
data class DownloadLyricsResponse(
  val content: String,
)
