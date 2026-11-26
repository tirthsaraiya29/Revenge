package com.revenge.music.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.DeleteTable
import androidx.room.RenameColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.revenge.music.db.daos.RecommendationExclusionDao
import com.revenge.music.db.daos.SongPlayStatsDao
import com.revenge.music.db.daos.SpeedDialDao
import com.revenge.music.db.daos.TasteProfileDao
import com.revenge.music.db.entities.AlbumArtistMap
import com.revenge.music.db.entities.AlbumEntity
import com.revenge.music.db.entities.ArtistEntity
import com.revenge.music.db.entities.BeatInfoEntity
import com.revenge.music.db.entities.Event
import com.revenge.music.db.entities.FormatEntity
import com.revenge.music.db.entities.LyricsEntity
import com.revenge.music.db.entities.PlayCountEntity
import com.revenge.music.db.entities.PlaylistEntity
import com.revenge.music.db.entities.PlaylistSongMap
import com.revenge.music.db.entities.PlaylistSongMapPreview
import com.revenge.music.db.entities.RecognitionHistory
import com.revenge.music.db.entities.RelatedSongMap
import com.revenge.music.db.entities.RecommendationExclusionEntity
import com.revenge.music.db.entities.SearchHistory
import com.revenge.music.db.entities.SetVideoIdEntity
import com.revenge.music.db.entities.SongAlbumMap
import com.revenge.music.db.entities.SongArtistMap
import com.revenge.music.db.entities.SongEntity
import com.revenge.music.db.entities.SongPlayStatsEntity
import com.revenge.music.db.entities.SortedSongAlbumMap
import com.revenge.music.db.entities.SortedSongArtistMap
import com.revenge.music.db.entities.SpeedDialItem
import com.revenge.music.db.entities.TasteProfileEntity
import java.util.concurrent.Executors
import timber.log.Timber

@Database(
    entities = [
        SongEntity::class,
        ArtistEntity::class,
        AlbumEntity::class,
        PlaylistEntity::class,
        SongArtistMap::class,
        SongAlbumMap::class,
        AlbumArtistMap::class,
        PlaylistSongMap::class,
        SearchHistory::class,
        FormatEntity::class,
        LyricsEntity::class,
        Event::class,
        RelatedSongMap::class,
        SetVideoIdEntity::class,
        PlayCountEntity::class,
        RecognitionHistory::class,
        SpeedDialItem::class,
        BeatInfoEntity::class,
        SongPlayStatsEntity::class,
        RecommendationExclusionEntity::class,
        TasteProfileEntity::class,
    ],
    views = [SortedSongArtistMap::class, SortedSongAlbumMap::class, PlaylistSongMapPreview::class],
    version = 48,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6, spec = Migration5To6::class),
        AutoMigration(from = 6, to = 7, spec = Migration6To7::class),
        AutoMigration(from = 7, to = 8, spec = Migration7To8::class),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10, spec = Migration9To10::class),
        AutoMigration(from = 10, to = 11, spec = Migration10To11::class),
        AutoMigration(from = 11, to = 12, spec = Migration11To12::class),
        AutoMigration(from = 12, to = 13, spec = Migration12To13::class),
        AutoMigration(from = 13, to = 14, spec = Migration13To14::class),
        AutoMigration(from = 14, to = 15),
        AutoMigration(from = 15, to = 16),
        AutoMigration(from = 16, to = 17, spec = Migration16To17::class),
        AutoMigration(from = 17, to = 18),
        AutoMigration(from = 18, to = 19, spec = Migration18To19::class),
        AutoMigration(from = 19, to = 20, spec = Migration19To20::class),
        AutoMigration(from = 20, to = 21, spec = Migration20To21::class),
        AutoMigration(from = 21, to = 22, spec = Migration21To22::class),
        AutoMigration(from = 22, to = 23, spec = Migration22To23::class),
        AutoMigration(from = 23, to = 24, spec = Migration23To24::class),
        AutoMigration(from = 24, to = 25),
        AutoMigration(from = 25, to = 26),
        AutoMigration(from = 26, to = 27),
        AutoMigration(from = 27, to = 28),
        AutoMigration(from = 30, to = 31),
        AutoMigration(from = 32, to = 33),
        AutoMigration(from = 33, to = 34),
        AutoMigration(from = 34, to = 35),
        AutoMigration(from = 35, to = 36),
        AutoMigration(from = 36, to = 37, spec = Migration36To37Spec::class),
        AutoMigration(from = 41, to = 42, spec = Migration41To42::class),
        AutoMigration(from = 46, to = 47, spec = Migration46To47Spec::class),
        AutoMigration(from = 47, to = 48, spec = Migration47To48Spec::class),
    ],
)
@TypeConverters(Converters::class)
abstract class InternalDatabase : RoomDatabase() {
    abstract val dao: DatabaseDao
    abstract val speedDialDao: SpeedDialDao
    abstract val songPlayStatsDao: SongPlayStatsDao
    abstract val recommendationExclusionDao: RecommendationExclusionDao
    abstract val tasteProfileDao: TasteProfileDao

    companion object {
        const val DB_NAME = "song.db"

        fun newInstance(context: Context): MusicDatabase {
            val database = Room.databaseBuilder(context, InternalDatabase::class.java, DB_NAME)
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_21_24,
                    MIGRATION_22_24,
                    MIGRATION_24_25,
                    MIGRATION_27_28,
                    MIGRATION_28_29,
                    MIGRATION_29_30,
                    MIGRATION_31_32,
                    MIGRATION_36_37,
                    MIGRATION_37_38,
                    MIGRATION_38_39,
                    MIGRATION_39_40,
                    MIGRATION_40_41,
                    MIGRATION_41_42,
                    MIGRATION_42_43,
                    MIGRATION_43_44,
                    MIGRATION_44_45,
                    MIGRATION_45_46,
                )
                .setQueryExecutor(Executors.newFixedThreadPool(4))
                .setTransactionExecutor(Executors.newSingleThreadExecutor())
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addCallback(
                    object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            listOf(
                                "PRAGMA busy_timeout = 60000",
                                "PRAGMA cache_size = -16000",
                                "PRAGMA wal_autocheckpoint = 1000",
                                "PRAGMA synchronous = NORMAL",
                            ).forEach { statement ->
                                runCatching { db.execSQL(statement) }
                                    .onFailure { Timber.e(it, "Failed to apply %s", statement) }
                            }
                            runCatching {
                                db.execSQL(
                                    "DELETE FROM format WHERE (codecs = 'alac' OR bitrate = 1411200) " +
                                        "AND id NOT LIKE 'local_%'",
                                )
                            }.onFailure { Timber.e(it, "Failed to prune legacy formats") }
                        }
                    },
                )
                .build()
            return MusicDatabase(database)
        }
    }
}

class MusicDatabase(private val delegate: InternalDatabase) : DatabaseDao by delegate.dao {
    val speedDialDao: SpeedDialDao get() = delegate.speedDialDao
    val songPlayStatsDao: SongPlayStatsDao get() = delegate.songPlayStatsDao
    val recommendationExclusionDao: RecommendationExclusionDao get() = delegate.recommendationExclusionDao
    val tasteProfileDao: TasteProfileDao get() = delegate.tasteProfileDao
    val openHelper: SupportSQLiteOpenHelper get() = delegate.openHelper

    fun query(block: MusicDatabase.() -> Unit) {
        delegate.queryExecutor.execute { runCatching { block(this) } }
    }

    fun transaction(block: MusicDatabase.() -> Unit) {
        delegate.transactionExecutor.execute {
            runCatching { delegate.runInTransaction(Runnable { block(this) }) }
        }
    }

    suspend fun withTransaction(block: suspend MusicDatabase.() -> Unit) {
        delegate.withTransaction { block() }
    }

    fun close() {
        delegate.close()
    }
}

// ----------------------------------------------------------------------
// Migration support helpers
// ----------------------------------------------------------------------

private fun SupportSQLiteDatabase.tableColumns(table: String): Set<String> =
    query("PRAGMA table_info(`$table`)").use { cursor ->
        val nameColumn = cursor.getColumnIndexOrThrow("name")
        buildSet {
            while (cursor.moveToNext()) {
                add(cursor.getString(nameColumn))
            }
        }
    }

private fun SupportSQLiteDatabase.hasTable(table: String): Boolean =
    query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table))
        .use { it.moveToFirst() }

private fun SupportSQLiteDatabase.addColumnIfMissing(table: String, column: String, definition: String) {
    if (column !in tableColumns(table)) {
        execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $definition")
    }
}

private fun SupportSQLiteDatabase.ensureView(name: String, body: String) {
    val present = query("SELECT name FROM sqlite_master WHERE type = 'view' AND name = ?", arrayOf(name))
        .use { it.moveToFirst() }
    if (!present) {
        execSQL("CREATE VIEW `$name` AS $body")
    }
}

private fun SupportSQLiteDatabase.ensureStandardViews() {
    ensureView("sorted_song_artist_map", "SELECT * FROM song_artist_map ORDER BY position")
    ensureView("sorted_song_album_map", "SELECT * FROM song_album_map ORDER BY `index`")
    ensureView(
        "playlist_song_map_preview",
        "SELECT * FROM playlist_song_map WHERE position <= 3 ORDER BY position",
    )
}

private fun SupportSQLiteDatabase.rebuild(
    table: String,
    sourceColumns: List<String>,
    create: (String) -> String,
) {
    val temporary = "${table}_migration_tmp"
    execSQL("DROP TABLE IF EXISTS `$temporary`")
    execSQL(create(temporary))
    val available = tableColumns(table)
    val copyColumns = sourceColumns.filter { it in available }
    val joined = copyColumns.joinToString(", ") { "`$it`" }
    if (joined.isNotEmpty()) {
        execSQL("INSERT INTO `$temporary` ($joined) SELECT $joined FROM `$table`")
    }
    execSQL("DROP TABLE `$table`")
    execSQL("ALTER TABLE `$temporary` RENAME TO `$table`")
}

// ----------------------------------------------------------------------
// v1 -> v2
// ----------------------------------------------------------------------

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA defer_foreign_keys = TRUE")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `song_migration_tmp` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`duration` INTEGER NOT NULL, `thumbnailUrl` TEXT, `albumId` TEXT, `albumName` TEXT, " +
                "`liked` INTEGER NOT NULL, `totalPlayTime` INTEGER NOT NULL, `isTrash` INTEGER NOT NULL, " +
                "`download_state` INTEGER NOT NULL, `create_date` INTEGER NOT NULL, " +
                "`modify_date` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `artist_migration_tmp` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`thumbnailUrl` TEXT, `bannerUrl` TEXT, `description` TEXT, `createDate` INTEGER NOT NULL, " +
                "`lastUpdateTime` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlist_migration_tmp` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`author` TEXT, `authorId` TEXT, `year` INTEGER, `thumbnailUrl` TEXT, " +
                "`createDate` INTEGER NOT NULL, `lastUpdateTime` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )

        val artistIds = LinkedHashMap<Long, String>()
        db.query("SELECT id, name FROM artist").use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow("id")
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                val oldId = if (cursor.isNull(idColumn)) {
                    -1L - artistIds.size
                } else {
                    cursor.getLong(idColumn)
                }
                artistIds[oldId] = ArtistEntity.generateArtistId()
                db.execSQL(
                    "INSERT INTO `artist_migration_tmp` (`id`, `name`, `thumbnailUrl`, `bannerUrl`, " +
                        "`description`, `createDate`, `lastUpdateTime`) VALUES (?, ?, NULL, NULL, NULL, 0, 0)",
                    arrayOf(artistIds[oldId], cursor.getString(nameColumn)),
                )
            }
        }

        val playlistIds = LinkedHashMap<Long, String>()
        db.query("SELECT playlistId, name FROM playlist").use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow("playlistId")
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                val oldId = cursor.getLong(idColumn)
                playlistIds[oldId] = PlaylistEntity.generatePlaylistId()
                db.execSQL(
                    "INSERT INTO `playlist_migration_tmp` (`id`, `name`, `author`, `authorId`, " +
                        "`year`, `thumbnailUrl`, `createDate`, `lastUpdateTime`) " +
                        "VALUES (?, ?, NULL, NULL, NULL, NULL, 0, 0)",
                    arrayOf(playlistIds[oldId], cursor.getString(nameColumn)),
                )
            }
        }

        db.execSQL(
            "INSERT INTO `song_migration_tmp` (`id`, `title`, `duration`, `thumbnailUrl`, " +
                "`albumId`, `albumName`, `liked`, `totalPlayTime`, `isTrash`, " +
                "`download_state`, `create_date`, `modify_date`) " +
                "SELECT `id`, `title`, `duration`, NULL, NULL, NULL, `liked`, 0, " +
                "`isTrash`, `download_state`, `create_date`, `modify_date` FROM `song`",
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `song_artist_map` (`songId` TEXT NOT NULL, " +
                "`artistId` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                "PRIMARY KEY(`songId`, `artistId`), " +
                "FOREIGN KEY(`songId`) REFERENCES `song`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`artistId`) REFERENCES `artist`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.query("SELECT id, artistId FROM song").use { cursor ->
            val songColumn = cursor.getColumnIndexOrThrow("id")
            val artistColumn = cursor.getColumnIndexOrThrow("artistId")
            while (cursor.moveToNext()) {
                val songId = cursor.getString(songColumn)
                val oldArtistId = cursor.getLong(artistColumn)
                var newArtistId = artistIds[oldArtistId]
                if (newArtistId == null) {
                    newArtistId = ArtistEntity.generateArtistId()
                    artistIds[oldArtistId] = newArtistId
                    db.execSQL(
                        "INSERT INTO `artist_migration_tmp` (`id`, `name`, `thumbnailUrl`, `bannerUrl`, " +
                            "`description`, `createDate`, `lastUpdateTime`) " +
                            "VALUES (?, '', NULL, NULL, NULL, 0, 0)",
                        arrayOf(newArtistId),
                    )
                }
                db.execSQL(
                    "INSERT OR IGNORE INTO `song_artist_map` (`songId`, `artistId`, `position`) VALUES (?, ?, 0)",
                    arrayOf(songId, newArtistId),
                )
            }
        }

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlist_song_map` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`playlistId` TEXT NOT NULL, `songId` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                "FOREIGN KEY(`playlistId`) REFERENCES `playlist`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`songId`) REFERENCES `song`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        val positions = HashMap<String, Int>()
        db.query("SELECT playlistId, songId FROM playlist_song ORDER BY playlistId, idInPlaylist, id").use { cursor ->
            val playlistColumn = cursor.getColumnIndexOrThrow("playlistId")
            val songColumn = cursor.getColumnIndexOrThrow("songId")
            while (cursor.moveToNext()) {
                val mappedPlaylist = playlistIds[cursor.getLong(playlistColumn)] ?: continue
                val songId = cursor.getString(songColumn)
                val position = positions.getOrDefault(mappedPlaylist, 0)
                db.execSQL(
                    "INSERT INTO `playlist_song_map` (`playlistId`, `songId`, `position`) VALUES (?, ?, ?)",
                    arrayOf(mappedPlaylist, songId, position),
                )
                positions[mappedPlaylist] = position + 1
            }
        }

        db.execSQL("DROP TABLE IF EXISTS `song`")
        db.execSQL("DROP TABLE IF EXISTS `artist`")
        db.execSQL("DROP TABLE IF EXISTS `playlist`")
        db.execSQL("ALTER TABLE `song_migration_tmp` RENAME TO `song`")
        db.execSQL("ALTER TABLE `artist_migration_tmp` RENAME TO `artist`")
        db.execSQL("ALTER TABLE `playlist_migration_tmp` RENAME TO `playlist`")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `album` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`year` INTEGER, `thumbnailUrl` TEXT, `songCount` INTEGER NOT NULL, " +
                "`duration` INTEGER NOT NULL, `createDate` INTEGER NOT NULL, " +
                "`lastUpdateTime` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `song_album_map` (`songId` TEXT NOT NULL, " +
                "`albumId` TEXT NOT NULL, `index` INTEGER, PRIMARY KEY(`songId`, `albumId`), " +
                "FOREIGN KEY(`songId`) REFERENCES `song`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`albumId`) REFERENCES `album`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `album_artist_map` (`albumId` TEXT NOT NULL, " +
                "`artistId` TEXT NOT NULL, `order` INTEGER NOT NULL, PRIMARY KEY(`albumId`, `artistId`), " +
                "FOREIGN KEY(`albumId`) REFERENCES `album`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`artistId`) REFERENCES `artist`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `search_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`query` TEXT NOT NULL)",
        )

        listOf(
            "CREATE INDEX IF NOT EXISTS `index_song_artist_map_songId` ON `song_artist_map` (`songId`)",
            "CREATE INDEX IF NOT EXISTS `index_song_artist_map_artistId` ON `song_artist_map` (`artistId`)",
            "CREATE INDEX IF NOT EXISTS `index_song_album_map_songId` ON `song_album_map` (`songId`)",
            "CREATE INDEX IF NOT EXISTS `index_song_album_map_albumId` ON `song_album_map` (`albumId`)",
            "CREATE INDEX IF NOT EXISTS `index_album_artist_map_albumId` ON `album_artist_map` (`albumId`)",
            "CREATE INDEX IF NOT EXISTS `index_album_artist_map_artistId` ON `album_artist_map` (`artistId`)",
            "CREATE INDEX IF NOT EXISTS `index_playlist_song_map_playlistId` ON `playlist_song_map` (`playlistId`)",
            "CREATE INDEX IF NOT EXISTS `index_playlist_song_map_songId` ON `playlist_song_map` (`songId`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_search_history_query` ON `search_history` (`query`)",
        ).forEach { statement -> db.execSQL(statement) }

        db.execSQL("DROP TABLE IF EXISTS `playlist_song`")

        db.execSQL("DROP VIEW IF EXISTS `sorted_song_artist_map`")
        db.execSQL("DROP VIEW IF EXISTS `playlist_song_map_preview`")
        db.execSQL(
            "CREATE VIEW `sorted_song_artist_map` AS SELECT * FROM song_artist_map ORDER BY position",
        )
        db.execSQL(
            "CREATE VIEW `playlist_song_map_preview` AS SELECT * FROM playlist_song_map " +
                "WHERE position <= 3 ORDER BY position",
        )
    }
}

// ----------------------------------------------------------------------
// 21 -> 24 / 22 -> 24 (explicit shortcuts)
// ----------------------------------------------------------------------

private fun applySongUploadColumns(db: SupportSQLiteDatabase) {
    db.addColumnIfMissing("song", "libraryAddToken", "TEXT")
    db.addColumnIfMissing("song", "libraryRemoveToken", "TEXT")
    db.addColumnIfMissing("song", "romanizeLyrics", "INTEGER NOT NULL DEFAULT 1")
    db.addColumnIfMissing("song", "isDownloaded", "INTEGER NOT NULL DEFAULT 0")
    db.addColumnIfMissing("song", "isUploaded", "INTEGER NOT NULL DEFAULT 0")
    db.addColumnIfMissing("album", "isUploaded", "INTEGER NOT NULL DEFAULT 0")
}

val MIGRATION_21_24 = object : Migration(21, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        applySongUploadColumns(db)
    }
}

val MIGRATION_22_24 = object : Migration(22, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        applySongUploadColumns(db)
    }
}

// ----------------------------------------------------------------------
// 24 -> 25
// ----------------------------------------------------------------------

val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA defer_foreign_keys = TRUE")
        db.rebuild(
            table = "format",
            sourceColumns = listOf(
                "id", "itag", "mimeType", "codecs", "bitrate", "sampleRate",
                "contentLength", "loudnessDb", "perceptualLoudnessDb", "playbackUrl",
            ),
        ) { name ->
            "CREATE TABLE IF NOT EXISTS `$name` (`id` TEXT NOT NULL, `itag` INTEGER NOT NULL, " +
                "`mimeType` TEXT NOT NULL, `codecs` TEXT NOT NULL, `bitrate` INTEGER NOT NULL, " +
                "`sampleRate` INTEGER, `contentLength` INTEGER NOT NULL, `loudnessDb` REAL, " +
                "`perceptualLoudnessDb` REAL, `playbackUrl` TEXT, PRIMARY KEY(`id`))"
        }
    }
}

// ----------------------------------------------------------------------
// 27 -> 28
// ----------------------------------------------------------------------

val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA defer_foreign_keys = TRUE")

        db.rebuild(
            "song",
            listOf(
                "id", "title", "duration", "thumbnailUrl", "albumId", "albumName", "explicit",
                "year", "date", "dateModified", "liked", "likedDate", "totalPlayTime",
                "inLibrary", "dateDownload", "isLocal", "libraryAddToken", "libraryRemoveToken",
                "lyricsOffset", "romanizeLyrics", "isDownloaded", "isUploaded", "isVideo",
            ),
        ) { name ->
            "CREATE TABLE IF NOT EXISTS `$name` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`duration` INTEGER NOT NULL, `thumbnailUrl` TEXT, `albumId` TEXT, `albumName` TEXT, " +
                "`explicit` INTEGER NOT NULL DEFAULT 0, `year` INTEGER, `date` INTEGER, " +
                "`dateModified` INTEGER, `liked` INTEGER NOT NULL, `likedDate` INTEGER, " +
                "`totalPlayTime` INTEGER NOT NULL, `inLibrary` INTEGER, `dateDownload` INTEGER, " +
                "`isLocal` INTEGER NOT NULL DEFAULT false, `libraryAddToken` TEXT, " +
                "`libraryRemoveToken` TEXT, `lyricsOffset` INTEGER NOT NULL DEFAULT 0, " +
                "`romanizeLyrics` INTEGER NOT NULL DEFAULT true, " +
                "`isDownloaded` INTEGER NOT NULL DEFAULT 0, " +
                "`isUploaded` INTEGER NOT NULL DEFAULT false, " +
                "`isVideo` INTEGER NOT NULL DEFAULT false, PRIMARY KEY(`id`))"
        }

        db.rebuild(
            "artist",
            listOf(
                "id", "name", "thumbnailUrl", "channelId", "lastUpdateTime", "bookmarkedAt", "isLocal",
            ),
        ) { name ->
            "CREATE TABLE IF NOT EXISTS `$name` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`thumbnailUrl` TEXT, `channelId` TEXT, `lastUpdateTime` INTEGER NOT NULL, " +
                "`bookmarkedAt` INTEGER, `isLocal` INTEGER NOT NULL DEFAULT false, PRIMARY KEY(`id`))"
        }

        db.rebuild(
            "album",
            listOf(
                "id", "playlistId", "title", "year", "thumbnailUrl", "themeColor", "songCount",
                "duration", "explicit", "lastUpdateTime", "bookmarkedAt", "likedDate", "inLibrary",
                "isLocal", "isUploaded",
            ),
        ) { name ->
            "CREATE TABLE IF NOT EXISTS `$name` (`id` TEXT NOT NULL, `playlistId` TEXT, " +
                "`title` TEXT NOT NULL, `year` INTEGER, `thumbnailUrl` TEXT, `themeColor` INTEGER, " +
                "`songCount` INTEGER NOT NULL, `duration` INTEGER NOT NULL, " +
                "`explicit` INTEGER NOT NULL DEFAULT 0, `lastUpdateTime` INTEGER NOT NULL, " +
                "`bookmarkedAt` INTEGER, `likedDate` INTEGER, `inLibrary` INTEGER, " +
                "`isLocal` INTEGER NOT NULL DEFAULT false, " +
                "`isUploaded` INTEGER NOT NULL DEFAULT false, PRIMARY KEY(`id`))"
        }

        db.rebuild(
            "playlist",
            listOf(
                "id", "name", "browseId", "createdAt", "lastUpdateTime", "isEditable", "bookmarkedAt",
                "remoteSongCount", "playEndpointParams", "thumbnailUrl", "shuffleEndpointParams",
                "radioEndpointParams", "isLocal",
            ),
        ) { name ->
            "CREATE TABLE IF NOT EXISTS `$name` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`browseId` TEXT, `createdAt` INTEGER, `lastUpdateTime` INTEGER, " +
                "`isEditable` INTEGER NOT NULL DEFAULT true, `bookmarkedAt` INTEGER, " +
                "`remoteSongCount` INTEGER, `playEndpointParams` TEXT, `thumbnailUrl` TEXT, " +
                "`shuffleEndpointParams` TEXT, `radioEndpointParams` TEXT, " +
                "`isLocal` INTEGER NOT NULL DEFAULT false, PRIMARY KEY(`id`))"
        }

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_albumId` ON `song` (`albumId`)")
        db.ensureStandardViews()
    }
}

// ----------------------------------------------------------------------
// Remaining explicit migrations
// ----------------------------------------------------------------------

val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing("playlist", "isAutoSync", "INTEGER NOT NULL DEFAULT false")
        db.ensureStandardViews()
    }
}

val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing("song", "isVideo", "INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("lyrics", "provider", "TEXT NOT NULL DEFAULT 'Unknown'")
        db.ensureStandardViews()
    }
}

val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing("lyrics", "provider", "TEXT NOT NULL DEFAULT 'Unknown'")
        db.addColumnIfMissing("lyrics", "translatedLyrics", "TEXT NOT NULL DEFAULT ''")
        db.addColumnIfMissing("lyrics", "translationLanguage", "TEXT NOT NULL DEFAULT ''")
        db.addColumnIfMissing("lyrics", "translationMode", "TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_36_37 = object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) = Unit
}

val MIGRATION_37_38 = object : Migration(37, 38) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `brain_activity_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT " +
                "NOT NULL, `action` TEXT NOT NULL, `reason` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `play_event` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`trackId` TEXT NOT NULL, `startTime` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, " +
                "`skipped` INTEGER NOT NULL, `engaged` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `taste_profile` (`id` INTEGER NOT NULL, `genres` TEXT NOT NULL, " +
                "`confidence` REAL NOT NULL, `patternsFound` INTEGER NOT NULL, " +
                "`modelVersion` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.addColumnIfMissing("format", "perceptualLoudnessDb", "REAL DEFAULT NULL")
    }
}

val MIGRATION_38_39 = object : Migration(38, 39) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing("format", "perceptualLoudnessDb", "REAL DEFAULT NULL")
    }
}

val MIGRATION_39_40 = object : Migration(39, 40) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `beat_info` (`songId` TEXT NOT NULL, `bpm` REAL NOT NULL, " +
                "`firstBeatOffsetMs` INTEGER NOT NULL, `confidence` REAL NOT NULL, " +
                "`analyzedAt` INTEGER NOT NULL, PRIMARY KEY(`songId`))",
        )
    }
}

val MIGRATION_40_41 = object : Migration(40, 41) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing("beat_info", "mixInPointMs", "INTEGER DEFAULT NULL")
        db.addColumnIfMissing("beat_info", "mixOutPointMs", "INTEGER DEFAULT NULL")
    }
}

val MIGRATION_41_42 = object : Migration(41, 42) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `brain_activity_log`")
        db.execSQL("DROP TABLE IF EXISTS `play_event`")
        db.execSQL("DROP TABLE IF EXISTS `taste_profile`")
    }
}

val MIGRATION_42_43 = object : Migration(42, 43) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing("playlist", "isPinned", "INTEGER NOT NULL DEFAULT false")
    }
}

val MIGRATION_43_44 = object : Migration(43, 44) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing("beat_info", "keyPitchClass", "INTEGER DEFAULT NULL")
        db.addColumnIfMissing("beat_info", "keyIsMinor", "INTEGER DEFAULT NULL")
    }
}

val MIGRATION_44_45 = object : Migration(44, 45) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing("song", "isLocal", "INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("artist", "isLocal", "INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("playlist", "isLocal", "INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_45_46 = object : Migration(45, 46) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing("song", "hideFromQuickPicks", "INTEGER NOT NULL DEFAULT 0")
    }
}

// ----------------------------------------------------------------------
// Auto-migration specs
// ----------------------------------------------------------------------

@RenameColumn(tableName = "song", fromColumnName = "download_state", toColumnName = "downloadState")
@RenameColumn(tableName = "song", fromColumnName = "create_date", toColumnName = "createDate")
@RenameColumn(tableName = "song", fromColumnName = "modify_date", toColumnName = "modifyDate")
@DeleteColumn(tableName = "playlist", columnName = "author")
@DeleteColumn(tableName = "playlist", columnName = "authorId")
@DeleteColumn(tableName = "playlist", columnName = "year")
@DeleteColumn(tableName = "playlist", columnName = "thumbnailUrl")
@DeleteColumn(tableName = "playlist", columnName = "createDate")
@DeleteColumn(tableName = "playlist", columnName = "lastUpdateTime")
class Migration5To6 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE `playlist` SET `browseId` = `id` WHERE `id` NOT LIKE 'LP%'")
    }
}

class Migration6To7 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE `song` SET `inLibrary` = `createDate`")
    }
}

@DeleteColumn(tableName = "song", columnName = "createDate")
@DeleteColumn(tableName = "song", columnName = "modifyDate")
class Migration7To8 : AutoMigrationSpec

@DeleteTable(tableName = "download")
class Migration9To10 : AutoMigrationSpec

@DeleteColumn(tableName = "song", columnName = "downloadState")
@DeleteColumn(tableName = "artist", columnName = "bannerUrl")
@DeleteColumn(tableName = "artist", columnName = "description")
@DeleteColumn(tableName = "artist", columnName = "createDate")
class Migration10To11 : AutoMigrationSpec

@DeleteColumn(tableName = "album", columnName = "createDate")
class Migration11To12 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE `album` SET `bookmarkedAt` = `lastUpdateTime`")
        db.execSQL(
            "INSERT OR IGNORE INTO `album` (`id`, `title`, `year`, `thumbnailUrl`, `themeColor`, " +
                "`songCount`, `duration`, `lastUpdateTime`, `bookmarkedAt`) " +
                "SELECT `albumId`, COALESCE(MAX(`albumName`), ''), NULL, NULL, NULL, 0, 0, 0, 0 " +
                "FROM `song` WHERE `albumId` IS NOT NULL GROUP BY `albumId`",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_albumId` ON `song` (`albumId`)")
    }
}

class Migration12To13 : AutoMigrationSpec

class Migration13To14 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        db.execSQL("UPDATE `playlist` SET `createdAt` = ?, `lastUpdateTime` = ?", arrayOf(now, now))
    }
}

@DeleteColumn(tableName = "song", columnName = "isLocal")
@DeleteColumn(tableName = "song", columnName = "localPath")
@DeleteColumn(tableName = "playlist", columnName = "isLocal")
class Migration16To17 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE `playlist` SET `bookmarkedAt` = `lastUpdateTime`")
        db.execSQL("UPDATE `playlist` SET `isEditable` = 1 WHERE `browseId` IS NOT NULL")
    }
}

class Migration18To19 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE `song` SET `explicit` = 0 WHERE `explicit` IS NULL")
    }
}

class Migration19To20 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE `song` SET `explicit` = 0 WHERE `explicit` IS NULL")
    }
}

@DeleteColumn(tableName = "song", columnName = "artistName")
class Migration20To21 : AutoMigrationSpec

class Migration21To22 : AutoMigrationSpec

class Migration22To23 : AutoMigrationSpec

class Migration23To24 : AutoMigrationSpec

class Migration36To37Spec : AutoMigrationSpec

@DeleteTable(tableName = "brain_activity_log")
@DeleteTable(tableName = "play_event")
@DeleteTable(tableName = "taste_profile")
class Migration41To42 : AutoMigrationSpec

class Migration46To47Spec : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT OR IGNORE INTO `song_play_stats` (`trackKey`, `title`, `artist`, `videoId`, " +
                "`artworkUrl`, `totalPlayTimeMs`, `playCount`, `skipCount`, `lastPlayedAtMillis`) " +
                "SELECT `trackKey`, `title`, `artist`, `videoId`, `artworkUrl`, " +
                "`totalPlayTimeMs`, `playCount`, 0, `lastPlayedAtMillis` FROM (" +
                "SELECT lower(trim(`song`.`title`) || '|' || trim(ifnull(`artist_name`, ''))) AS `trackKey`, " +
                "`song`.`title` AS `title`, ifnull(`artist_name`, '') AS `artist`, " +
                "`song`.`id` AS `videoId`, `song`.`thumbnailUrl` AS `artworkUrl`, " +
                "sum(`event`.`playTime`) AS `totalPlayTimeMs`, count(*) AS `playCount`, " +
                "max(`event`.`timestamp`) AS `lastPlayedAtMillis` FROM `event` " +
                "JOIN `song` ON `song`.`id` = `event`.`songId` " +
                "LEFT JOIN (SELECT `song_artist_map`.`songId` AS `sid`, " +
                "group_concat(`artist`.`name`, ', ') AS `artist_name` FROM `song_artist_map` " +
                "JOIN `artist` ON `artist`.`id` = `song_artist_map`.`artistId` " +
                "GROUP BY `song_artist_map`.`songId`) AS `artists` ON `artists`.`sid` = `song`.`id` " +
                "WHERE `event`.`playTime` > 0 GROUP BY `trackKey`)",
        )
    }
}

class Migration47To48Spec : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        val columns = db.tableColumns("taste_profile")
        val needsRebuild = columns.isNotEmpty() &&
            ("topArtistsJson" !in columns || "genres" in columns)
        if (needsRebuild) {
            db.execSQL("DROP TABLE IF EXISTS `taste_profile`")
        }
        if (needsRebuild || columns.isEmpty()) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `taste_profile` (`id` INTEGER NOT NULL, " +
                    "`topArtistsJson` TEXT NOT NULL, `topTracksJson` TEXT NOT NULL, " +
                    "`topGenresJson` TEXT NOT NULL, `confidence` REAL NOT NULL, " +
                    "`updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
        }
    }
}
