package com.revenge.music.localmedia

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.revenge.music.db.MusicDatabase
import com.revenge.music.db.entities.AlbumArtistMap
import com.revenge.music.db.entities.AlbumEntity
import com.revenge.music.db.entities.ArtistEntity
import com.revenge.music.db.entities.SongAlbumMap
import com.revenge.music.db.entities.SongArtistMap
import com.revenge.music.db.entities.SongEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class LocalFolderFilterMode { EXCLUDE, INCLUDE }

data class LocalSongScanConfig(
    val minimumDurationSeconds: Int = 0,
    val excludedFolders: List<String> = emptyList(),
    val includedFolders: List<String> = emptyList(),
    val filterMode: LocalFolderFilterMode = LocalFolderFilterMode.EXCLUDE,
    val allowClearAll: Boolean = false,
) {
    val sanitizedMinimumDurationSeconds: Int get() = minimumDurationSeconds.coerceAtLeast(0)
    val normalizedExcludedFolders: List<String> get() = normalizeFolders(excludedFolders)
    val normalizedIncludedFolders: List<String> get() = normalizeFolders(includedFolders)

    val hasActiveFolderFilter: Boolean
        get() = if (filterMode == LocalFolderFilterMode.INCLUDE) {
            normalizedIncludedFolders.isNotEmpty()
        } else {
            normalizedExcludedFolders.isNotEmpty()
        }
}

data class LocalSongScanSummary(
    val scannedSongs: Int,
    val removedSongs: Int,
)

private fun normalizeFolders(folders: List<String>): List<String> {
    val seen = LinkedHashMap<String, String>()
    for (raw in folders) {
        val normalized = raw.trim()
            .replace('\\', '/')
            .replace(Regex("/{2,}"), "/")
            .trim('/')
        if (normalized.isEmpty()) continue
        seen.putIfAbsent(normalized.lowercase(Locale.ROOT), normalized)
    }
    return seen.values.toList()
}

private fun folderMatches(folder: String, entry: String): Boolean {
    val normalizedEntry = entry.lowercase(Locale.ROOT)
    return folder == normalizedEntry ||
        folder.startsWith("$normalizedEntry/") ||
        folder.endsWith("/$normalizedEntry") ||
        folder.contains("/$normalizedEntry/")
}

@Singleton
class LocalSongScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {
    companion object {
        const val BATCH_SIZE = 900
        private const val UNKNOWN = "<unknown>"
        private const val MEDIA_URI_PREFIX = "content://media/external/audio/media/"
        private const val ALBUM_ART_BASE = "content://media/external/audio/albumart/"
    }

    private data class ScannedRow(
        val song: SongEntity,
        val artists: List<ArtistEntity>,
        val album: AlbumEntity?,
        val primaryArtistId: String,
        val albumArtistIds: List<String>,
    )

    private data class QueryResult(
        val deviceRows: Int,
        val rows: List<ScannedRow>,
    )

    suspend fun scanDevice(
        config: LocalSongScanConfig = LocalSongScanConfig(),
    ): LocalSongScanSummary = withContext(Dispatchers.IO) {
        val query = queryMediaStore(config)
        var scanned = 0
        var removed = 0

        database.withTransaction {
            val existingIds = database.localSongIds()
            val scannedIds = query.rows.map { it.song.id }
            val removedIds = existingIds.filterNot { it in scannedIds }

            if (query.rows.isEmpty()) {
                val shouldClear = config.allowClearAll ||
                    existingIds.isEmpty() ||
                    (query.deviceRows > 0 && config.hasActiveFolderFilter)
                if (shouldClear) {
                    database.clearLocalSongs()
                    database.pruneLocalAlbums()
                    database.pruneLocalArtists()
                    database.pruneFormats()
                    database.prunePlayCounts()
                    removed = removedIds.size
                }
                return@withTransaction
            }

            removedIds.chunked(BATCH_SIZE).forEach { chunk ->
                database.deleteSongsByIds(chunk)
            }
            removed = removedIds.size

            persistRows(query.rows)
            database.pruneLocalAlbums()
            database.pruneLocalArtists()
            database.pruneFormats()
            database.prunePlayCounts()
            scanned = query.rows.size
        }

        LocalSongScanSummary(scannedSongs = scanned, removedSongs = removed)
    }

    private suspend fun persistRows(rows: List<ScannedRow>) {
        val songIds = rows.map { it.song.id }
        val existingSongs = runCatching { database.getSongsByIds(songIds) }
            .getOrDefault(emptyList())
            .associateBy { it.song.id }
        val existingArtists = runCatching {
            database.getArtistEntitiesByIds(rows.flatMap { it.artists }.map { it.id }.distinct())
        }.getOrDefault(emptyList()).associateBy { it.id }
        val existingAlbums = runCatching {
            database.getAlbumEntitiesByIds(rows.mapNotNull { it.album?.id }.distinct())
        }.getOrDefault(emptyList()).associateBy { it.id }

        for (row in rows) {
            for (artist in row.artists) {
                val previous = existingArtists[artist.id]
                val merged = artist.copy(
                    thumbnailUrl = previous?.thumbnailUrl ?: artist.thumbnailUrl,
                    lastUpdateTime = previous?.lastUpdateTime ?: artist.lastUpdateTime,
                    bookmarkedAt = previous?.bookmarkedAt,
                    channelId = previous?.channelId,
                    isLocal = true,
                )
                if (previous != null) database.update(merged) else database.insert(merged)
            }

            row.album?.let { album ->
                val previous = existingAlbums[album.id]
                val thumbnail = album.thumbnailUrl?.takeUnless { it.startsWith(MEDIA_URI_PREFIX) }
                val merged = album.copy(
                    thumbnailUrl = thumbnail ?: previous?.thumbnailUrl,
                    themeColor = previous?.themeColor,
                    bookmarkedAt = previous?.bookmarkedAt,
                    likedDate = previous?.likedDate,
                    inLibrary = previous?.inLibrary,
                    songCount = previous?.songCount?.takeIf { it > 0 } ?: album.songCount,
                    duration = previous?.duration?.takeIf { it > 0 } ?: album.duration,
                    isLocal = true,
                )
                if (previous != null) database.update(merged) else database.insert(merged)
                database.deleteAlbumArtistMapsByAlbumIds(listOf(album.id))
                row.albumArtistIds.forEachIndexed { index, artistId ->
                    database.insert(AlbumArtistMap(album.id, artistId, index))
                }
            }

            val previous = existingSongs[row.song.id]?.song
            val thumbnail = row.song.thumbnailUrl?.takeUnless { it.startsWith(MEDIA_URI_PREFIX) }
            val merged = row.song.copy(
                thumbnailUrl = thumbnail,
                explicit = previous?.explicit ?: row.song.explicit,
                liked = previous?.liked ?: row.song.liked,
                likedDate = previous?.likedDate,
                totalPlayTime = previous?.totalPlayTime ?: row.song.totalPlayTime,
                date = previous?.date,
                dateDownload = previous?.dateDownload,
                lyricsOffset = previous?.lyricsOffset ?: row.song.lyricsOffset,
                romanizeLyrics = previous?.romanizeLyrics ?: row.song.romanizeLyrics,
                isDownloaded = previous?.isDownloaded ?: row.song.isDownloaded,
            )
            database.upsert(merged)
            database.deleteSongArtistMaps(merged.id)
            row.artists.forEachIndexed { index, artist ->
                database.insert(SongArtistMap(merged.id, artist.id, index))
            }
            database.deleteSongAlbumMaps(merged.id)
            row.album?.let { album ->
                database.insert(SongAlbumMap(merged.id, album.id, 0))
            }
        }
    }

    private fun queryMediaStore(config: LocalSongScanConfig): QueryResult {
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ARTIST_ID)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.MIME_TYPE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.Audio.Media.DATA)
            }
        }.toTypedArray()

        val selection = buildList {
            add("${MediaStore.Audio.Media.SIZE} > 0")
            val minimum = config.sanitizedMinimumDurationSeconds
            if (minimum > 0) {
                add("${MediaStore.Audio.Media.DURATION} >= ${minimum * 1000L}")
            } else {
                add("${MediaStore.Audio.Media.DURATION} > 0")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add("${MediaStore.Audio.Media.IS_PENDING} = 0")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add("is_trashed = 0")
            }
        }.joinToString(" AND ")

        @Suppress("DEPRECATION")
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC, " +
            "${MediaStore.Audio.Media._ID} ASC"

        val cursor = runCatching {
            resolver.query(collection, projection, selection, null, sortOrder)
        }.getOrNull() ?: return QueryResult(0, emptyList())

        cursor.use { c ->
            val deviceRows = c.count
            val rows = ArrayList<ScannedRow>()
            while (c.moveToNext()) {
                val row = readRow(c, config) ?: continue
                rows += row
            }
            return QueryResult(deviceRows, rows)
        }
    }

    private fun readRow(
        cursor: android.database.Cursor,
        config: LocalSongScanConfig,
    ): ScannedRow? {
        val mediaId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
        val displayName = cursor.getStringOrNull(MediaStore.Audio.Media.DISPLAY_NAME)
        val mimeType = cursor.getStringOrNull(MediaStore.Audio.Media.MIME_TYPE)
        if (!SupportedLocalAudio.isSupported(displayName, mimeType)) return null

        val folder = folderOf(cursor)
        if (config.hasActiveFolderFilter) {
            val entries = if (config.filterMode == LocalFolderFilterMode.INCLUDE) {
                config.normalizedIncludedFolders
            } else {
                config.normalizedExcludedFolders
            }
            val matches = entries.any { folderMatches(folder, it) }
            if (config.filterMode == LocalFolderFilterMode.INCLUDE && !matches) return null
            if (config.filterMode == LocalFolderFilterMode.EXCLUDE && matches) return null
        }

        val rawTitle = cursor.getStringOrNull(MediaStore.Audio.Media.TITLE)
            ?: displayName?.substringBeforeLast('.')
        val title = rawTitle?.takeIf { it.isNotBlank() }
            ?: context.getString(android.R.string.unknownName)
        val rawArtist = cursor.getStringOrNull(MediaStore.Audio.Media.ARTIST)
        val unknownArtist = context.getString(android.R.string.unknownName)
        val artistNames = rawArtist
            ?.takeUnless { it.isBlank() || it.equals(UNKNOWN, ignoreCase = true) }
            ?.split(Regex("[,;/&]"))
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
            .ifEmpty { listOf(unknownArtist) }
        val mediaArtistId = cursor.getLong(
            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID),
        )
        val artists = artistNames.mapIndexed { index, name ->
            val id = if (artistNames.size == 1 && mediaArtistId > 0) {
                "LOCAL_ARTIST_$mediaArtistId"
            } else {
                "LOCAL_ARTIST_${stableHash("$name|$index")}"
            }
            ArtistEntity(id = id, name = name, isLocal = true)
        }

        val rawAlbum = cursor.getStringOrNull(MediaStore.Audio.Media.ALBUM)
        val albumName = rawAlbum?.takeUnless { it.isBlank() || it.equals(UNKNOWN, ignoreCase = true) }
        val mediaAlbumId = cursor.getLong(
            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID),
        )
        val primaryArtistId = artists.first().id
        val albumId = when {
            mediaAlbumId > 0 -> "LOCAL_ALBUM_$mediaAlbumId"
            albumName != null -> "LOCAL_ALBUM_${stableHash("$albumName|$primaryArtistId")}"
            else -> null
        }
        val durationMs = cursor.getLong(
            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION),
        )
        val year = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR))
        val dateModifiedSeconds = cursor.getLong(
            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED),
        )
        val trackUri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            mediaId,
        ).toString()
        val thumbnail = if (mediaAlbumId > 0) "$ALBUM_ART_BASE$mediaAlbumId" else trackUri
        val isVideo = mimeType?.startsWith("video/") == true

        val song = SongEntity(
            id = trackUri,
            title = title,
            duration = (durationMs / 1000L).toInt().coerceAtLeast(0),
            thumbnailUrl = thumbnail,
            albumId = albumId,
            albumName = albumName,
            explicit = false,
            year = year.takeIf { it > 0 },
            dateModified = dateModifiedSeconds.takeIf { it > 0 }?.let { seconds ->
                LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(seconds),
                    ZoneId.systemDefault(),
                )
            },
            isLocal = true,
            isVideo = isVideo,
        )

        val album = albumName?.let { name ->
            AlbumEntity(
                id = albumId ?: "LOCAL_ALBUM_${stableHash("$name|$primaryArtistId")}",
                title = name,
                thumbnailUrl = thumbnail,
                songCount = 1,
                duration = (durationMs / 1000L).toInt().coerceAtLeast(0),
                isLocal = true,
            )
        }

        return ScannedRow(
            song = song,
            artists = artists,
            album = album,
            primaryArtistId = primaryArtistId,
            albumArtistIds = listOf(primaryArtistId),
        )
    }

    private fun folderOf(cursor: android.database.Cursor): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relative = cursor.getStringOrNull(MediaStore.Audio.Media.RELATIVE_PATH)
            if (!relative.isNullOrBlank()) {
                return relative.replace('\\', '/').trim('/').lowercase(Locale.ROOT)
            }
        }
        @Suppress("DEPRECATION")
        val data = cursor.getStringOrNull(MediaStore.Audio.Media.DATA)
        return data?.substringBeforeLast('/', "")
            ?.replace('\\', '/')
            ?.trim('/')
            ?.lowercase(Locale.ROOT)
            .orEmpty()
    }

    private fun android.database.Cursor.getStringOrNull(column: String): String? {
        val index = getColumnIndex(column)
        if (index < 0 || isNull(index)) return null
        return getString(index)
    }

    private fun stableHash(value: String): String =
        UUID.nameUUIDFromBytes(value.toByteArray(Charsets.UTF_8)).toString().replace("-", "")
}
