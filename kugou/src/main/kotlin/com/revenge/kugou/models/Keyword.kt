package com.revenge.kugou.models

/** Normalized search terms sent to the KuGou search endpoints. */
data class Keyword(val title: String, val artist: String, val album: String? = null)
