package com.revenge.music.db.entities

import com.revenge.metadata.cleaner.MetadataCleaner
import com.revenge.metadata.models.CleanedMetadata

val SongEntity.cleanedMetadata: CleanedMetadata
    get() = MetadataCleaner.clean(title, "")

val Song.cleanedMetadata: CleanedMetadata
    get() = MetadataCleaner.clean(title, artists.firstOrNull()?.name.orEmpty())

val Song.displayTitle: String
    get() = cleanedMetadata.toDisplayTitle()

val Song.cleanArtistName: String
    get() = cleanedMetadata.primaryArtist
