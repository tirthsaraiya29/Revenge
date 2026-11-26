package com.revenge.domain.models

data class Playlist(
    val id: String,
    val name: String,
    val songCount: Int = 0,
    val thumbnailUrl: String? = null,
)
