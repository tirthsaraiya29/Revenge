package com.revenge.music.db

import android.annotation.SuppressLint
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.contentValuesOf
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
import com.revenge.music.db.entities.RecommendationExclusionEntity
import com.revenge.music.db.entities.RelatedSongMap
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
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Date
import timber.log.Timber

/**
 * Facade used by feature code: exposes the shared DAO plus the small side-table DAOs and the
 * transaction/executor helpers that Room keeps on the database instance.
 */
class MusicDatabase(
  private val delegate: InternalDatabase,
) : DatabaseDao by delegate.dao {
  val speedDialDao: SpeedDialDao
    get() = delegate.speedDialDao

  val songPlayStatsDao: SongPlayStatsDao
    get() = delegate.songPlayStatsDao

  val recommendationExclusionDao: RecommendationExclusionDao
    get() = delegate.recommendationExclusionDao

  val tasteProfileDao: TasteProfileDao
    get() = delegate.tasteProfileDao

  val openHelper: SupportSQLiteOpenHelper
    get() = delegate.openHelper

  fun query(block: MusicDatabase.() -> Unit) =
    with(delegate) { queryExecutor.execute { block(this@MusicDatabase) } }

  fun transaction(block: MusicDatabase.() -> Unit) =
    with(delegate) {
      transactionExecutor.execute { runInTransaction { block(this@MusicDatabase) } }
    }

  suspend fun withTransaction(block: suspend MusicDatabase.() -> Unit) = delegate.withTransaction {
    block(this@MusicDatabase)
  }

  fun close() = delegate.close()
}

@Database(
  entities =
    [
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
  autoMigrations =
    [
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

    fun newInstance(context: Context): MusicDatabase =
      MusicDatabase(
        delegate =
          Room.databaseBuilder(context, InternalDatabase::class.java, DB_NAME)
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
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .setTransactionExecutor(java.util.concurrent.Executors.newFixedThreadPool(4))
            .setQueryExecutor(java.util.concurrent.Executors.newFixedThreadPool(4))
            .addCallback(
              object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                  super.onOpen(db)
                  try {
                    db.query("PRAGMA busy_timeout = 60000").close()
                    db.query("PRAGMA cache_size = -16000").close()
                    db.query("PRAGMA wal_autocheckpoint = 1000").close()
                    db.query("PRAGMA synchronous = NORMAL").close()
                  } catch (e: Exception) {
                    Timber.tag("MusicDatabase").e(e, "Failed to set PRAGMA settings")
                  }
                }
              },
            )
            .build(),
      )
  }
}

private fun hasColumn(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
  db.query("PRAGMA table_info('$table')").use { cursor ->
    val nameIndex = cursor.getColumnIndex("name")
    if (nameIndex < 0) return false
    while (cursor.moveToNext()) {
      if (cursor.getString(nameIndex) == column) return true
    }
  }
  return false
}

// ---------------------------------------------------------------------------------------------
// Legacy migrations (v1 - v46)
// ---------------------------------------------------------------------------------------------

val MIGRATION_1_2 =
  object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
      data class OldSong(
        val id: String,
        val title: String,
        val duration: Int = -1,
        val liked: Boolean = false,
        val totalPlayTime: Long = 0,
        val downloadState: Int = 0,
        val createDate: LocalDateTime = LocalDateTime.now(),
        val modifyDate: LocalDateTime = LocalDateTime.now(),
      )

      val converters = Converters()

      val artistIdMap = mutableMapOf<Int, String>()
      val artists = mutableListOf<ArtistEntity>()
      db.query("SELECT * FROM artist").use { cursor ->
        while (cursor.moveToNext()) {
          val oldId = cursor.getInt(0)
          val newId = ArtistEntity.generateArtistId()
          artistIdMap[oldId] = newId
          artists += ArtistEntity(id = newId, name = cursor.getString(1))
        }
      }

      val playlistIdMap = mutableMapOf<Int, String>()
      val playlists = mutableListOf<PlaylistEntity>()
      db.query("SELECT * FROM playlist").use { cursor ->
        while (cursor.moveToNext()) {
          val oldId = cursor.getInt(0)
          val newId = PlaylistEntity.generatePlaylistId()
          playlistIdMap[oldId] = newId
          playlists += PlaylistEntity(id = newId, name = cursor.getString(1))
        }
      }

      val rawPlaylistSongs = mutableListOf<PlaylistSongMap>()
      db.query("SELECT * FROM playlist_song").use { cursor ->
        while (cursor.moveToNext()) {
          rawPlaylistSongs +=
            PlaylistSongMap(
              playlistId = playlistIdMap[cursor.getInt(1)]!!,
              songId = cursor.getString(2),
              position = cursor.getInt(3),
            )
        }
      }
      // Old versions could have gaps or duplicates, so normalize positions per playlist.
      rawPlaylistSongs.sortBy { it.position }
      val nextPosition = mutableMapOf<String, Int>()
      val playlistSongs =
        rawPlaylistSongs.map { map ->
          val position = nextPosition[map.playlistId] ?: 0
          nextPosition[map.playlistId] = position + 1
          map.copy(position = position)
        }

      val songs = mutableListOf<OldSong>()
      val songArtistMaps = mutableListOf<SongArtistMap>()
      db.query("SELECT * FROM song").use { cursor ->
        while (cursor.moveToNext()) {
          val songId = cursor.getString(0)
          songs +=
            OldSong(
              id = songId,
              title = cursor.getString(1),
              duration = cursor.getInt(3),
              liked = cursor.getInt(4) == 1,
              createDate = epochToLocal(cursor.getLong(8)),
              modifyDate = epochToLocal(cursor.getLong(9)),
            )
          songArtistMaps +=
            SongArtistMap(
              songId = songId,
              artistId = artistIdMap[cursor.getInt(2)]!!,
              position = 0,
            )
        }
      }

      listOf("song", "artist", "playlist", "playlist_song").forEach {
        db.execSQL("DROP TABLE IF EXISTS $it")
      }

      db.execSQL(
        "CREATE TABLE IF NOT EXISTS `song` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
          "`duration` INTEGER NOT NULL, `thumbnailUrl` TEXT, `albumId` TEXT, `albumName` TEXT, " +
          "`liked` INTEGER NOT NULL, `totalPlayTime` INTEGER NOT NULL, `isTrash` INTEGER NOT NULL, " +
          "`download_state` INTEGER NOT NULL, `create_date` INTEGER NOT NULL, " +
          "`modify_date` INTEGER NOT NULL, PRIMARY KEY(`id`))",
      )
      db.execSQL(
        "CREATE TABLE IF NOT EXISTS `artist` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
          "`thumbnailUrl` TEXT, `bannerUrl` TEXT, `description` TEXT, `createDate` INTEGER NOT NULL, " +
          "`lastUpdateTime` INTEGER NOT NULL, PRIMARY KEY(`id`))",
      )
      db.execSQL(
        "CREATE TABLE IF NOT EXISTS `album` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
          "`year` INTEGER, `thumbnailUrl` TEXT, `songCount` INTEGER NOT NULL, " +
          "`duration` INTEGER NOT NULL, `createDate` INTEGER NOT NULL, " +
          "`lastUpdateTime` INTEGER NOT NULL, PRIMARY KEY(`id`))",
      )
      db.execSQL(
        "CREATE TABLE IF NOT EXISTS `playlist` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
          "`author` TEXT, `authorId` TEXT, `year` INTEGER, `thumbnailUrl` TEXT, " +
          "`createDate` INTEGER NOT NULL, `lastUpdateTime` INTEGER NOT NULL, PRIMARY KEY(`id`))",
      )
      db.execSQL(
        "CREATE TABLE IF NOT EXISTS `song_artist_map` (`songId` TEXT NOT NULL, " +
          "`artistId` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`songId`, `artistId`), " +
          "FOREIGN KEY(`songId`) REFERENCES `song`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
          "FOREIGN KEY(`artistId`) REFERENCES `artist`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
      )
      db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_song_artist_map_songId` ON `song_artist_map` (`songId`)",
      )
      db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_song_artist_map_artistId` ON `song_artist_map` (`artistId`)",
      )
      db.execSQL(
        "CREATE TABLE IF NOT EXISTS `song_album_map` (`songId` TEXT NOT NULL, `albumId` TEXT NOT NULL, " +
          "`index` INTEGER, PRIMARY KEY(`songId`, `albumId`), " +
          "FOREIGN KEY(`songId`) REFERENCES `song`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
          "FOREIGN KEY(`albumId`) REFERENCES `album`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
      )
      db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_song_album_map_songId` ON `song_album_map` (`songId`)",
      )
      db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_song_album_map_albumId` ON `song_album_map` (`albumId`)",
      )
      db.execSQL(
        "CREATE TABLE IF NOT EXISTS `album_artist_map` (`albumId` TEXT NOT NULL, `artistId` TEXT NOT NULL, " +
          "`order` INTEGER NOT NULL, PRIMARY KEY(`albumId`, `artistId`), " +
          "FOREIGN KEY(`albumId`) REFERENCES `album`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
          "FOREIGN KEY(`artistId`) REFERENCES `artist`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
      )
      db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_album_artist_map_albumId` ON `album_artist_map` (`albumId`)",
      )
      db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_album_artist_map_artistId` ON `album_artist_map` (`artistId`)",
      )
      db.execSQL(
        "CREATE TABLE IF NOT EXISTS `playlist_song_map` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
          "`playlistId` TEXT NOT NULL, `songId` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
          "FOREIGN KEY(`playlistId`) REFERENCES `playlist`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
          "FOREIGN KEY(`songId`) REFERENCES `song`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
      )
      db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_playlist_song_map_playlistId` " +
          "ON `playlist_song_map` (`playlistId`)",
      )
      db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_playlist_song_map_songId` ON `playlist_song_map` (`songId`)",
      )
      db.execSQL(
        "CREATE TABLE IF NOT EXISTS `download` (`id` INTEGER NOT NULL, `songId` TEXT NOT NULL, " +
          "PRIMARY KEY(`id`))",
      )
      db.execSQL(
        "CREATE TABLE IF NOT EXISTS `search_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
          "`query` TEXT NOT NULL)",
      )
      db.execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_search_history_query` ON `search_history` (`query`)",
      )
      db.execSQL(
        "CREATE VIEW `sorted_song_artist_map` AS SELECT * FROM song_artist_map ORDER BY position",
      )
      db.execSQL(
        "CREATE VIEW `playlist_song_map_preview` AS " +
          "SELECT * FROM playlist_song_map WHERE position <= 3 ORDER BY position",
      )

      artists.forEach { artist ->
        db.insert(
          "artist",
          SQLiteDatabase.CONFLICT_ABORT,
          contentValuesOf(
            "id" to artist.id,
            "name" to artist.name,
            "createDate" to converters.dateToTimestamp(artist.lastUpdateTime),
            "lastUpdateTime" to converters.dateToTimestamp(artist.lastUpdateTime),
          ),
        )
      }
      songs.forEach { song ->
        db.insert(
          "song",
          SQLiteDatabase.CONFLICT_ABORT,
          contentValuesOf(
            "id" to song.id,
            "title" to song.title,
            "duration" to song.duration,
            "liked" to song.liked,
            "totalPlayTime" to song.totalPlayTime,
            "isTrash" to false,
            "download_state" to song.downloadState,
            "create_date" to converters.dateToTimestamp(song.createDate),
            "modify_date" to converters.dateToTimestamp(song.modifyDate),
          ),
        )
      }
      songArtistMaps.forEach { map ->
        db.insert(
          "song_artist_map",
          SQLiteDatabase.CONFLICT_ABORT,
          contentValuesOf("songId" to map.songId, "artistId" to map.artistId, "position" to map.position),
        )
      }
      playlists.forEach { playlist ->
        val now = converters.dateToTimestamp(LocalDateTime.now())
        db.insert(
          "playlist",
          SQLiteDatabase.CONFLICT_ABORT,
          contentValuesOf(
            "id" to playlist.id,
            "name" to playlist.name,
            "createDate" to now,
            "lastUpdateTime" to now,
          ),
        )
      }
      playlistSongs.forEach { map ->
        db.insert(
          "playlist_song_map",
          SQLiteDatabase.CONFLICT_ABORT,
          contentValuesOf(
            "playlistId" to map.playlistId,
            "songId" to map.songId,
            "position" to map.position,
          ),
        )
      }
    }
  }

private fun epochToLocal(millis: Long): LocalDateTime =
  Instant.ofEpochMilli(Date(millis).time).atZone(ZoneOffset.UTC).toLocalDateTime()

val MIGRATION_21_24 =
  object : Migration(21, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
      addColumnIfMissing(db, "song", "libraryAddToken", "TEXT DEFAULT ''")
      addColumnIfMissing(db, "song", "libraryRemoveToken", "TEXT DEFAULT ''")
      addColumnIfMissing(db, "song", "romanizeLyrics", "INTEGER NOT NULL DEFAULT 1")
      addColumnIfMissing(db, "song", "isDownloaded", "INTEGER NOT NULL DEFAULT 0")
      addColumnIfMissing(db, "song", "isUploaded", "INTEGER NOT NULL DEFAULT 0")
    }
  }

val MIGRATION_22_24 =
  object : Migration(22, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
      addColumnIfMissing(db, "song", "isUploaded", "INTEGER NOT NULL DEFAULT 0")
    }
  }

val MIGRATION_24_25 =
  object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
      addColumnIfMissing(db, "format", "perceptualLoudnessDb", "REAL DEFAULT NULL")
    }
  }

val MIGRATION_27_28 =
  object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
      // Version 27 shipped without these columns for some users.
      addColumnIfMissing(db, "song", "lyricsOffset", "INTEGER NOT NULL DEFAULT 0")
      addColumnIfMissing(db, "song", "isVideo", "INTEGER NOT NULL DEFAULT 0")

      val hasLyricsOffset = hasColumn(db, "song", "lyricsOffset")
      val hasIsVideo = hasColumn(db, "song", "isVideo")
      val lyricsOffset = if (hasLyricsOffset) "`lyricsOffset`" else "0"
      val isVideo = if (hasIsVideo) "`isVideo`" else "0"

      dropViews(db)
      db.execSQL(
        "CREATE TABLE IF NOT EXISTS `_new_song` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
          "`duration` INTEGER NOT NULL, `thumbnailUrl` TEXT, `albumId` TEXT, `albumName` TEXT, " +
          "`explicit` INTEGER NOT NULL DEFAULT 0, `year` INTEGER, `date` INTEGER, " +
          "`dateModified` INTEGER, `liked` INTEGER NOT NULL, `likedDate` INTEGER, " +
          "`totalPlayTime` INTEGER NOT NULL, `inLibrary` INTEGER, `dateDownload` INTEGER, " +
          "`isLocal` INTEGER NOT NULL DEFAULT false, `libraryAddToken` TEXT, " +
          "`libraryRemoveToken` TEXT, `lyricsOffset` INTEGER NOT NULL DEFAULT 0, " +
          "`romanizeLyrics` INTEGER NOT NULL DEFAULT true, " +
          "`isDownloaded` INTEGER NOT NULL DEFAULT 0, " +
          "`isUploaded` INTEGER NOT NULL DEFAULT false, " +
          "`isVideo` INTEGER NOT NULL DEFAULT false, PRIMARY KEY(`id`))",
      )
      db.execSQL(
        "INSERT INTO `_new_song` (`id`,`title`,`duration`,`thumbnailUrl`,`albumId`,`albumName`," +
          "`explicit`,`year`,`date`,`dateModified`,`liked`,`likedDate`,`totalPlayTime`,`inLibrary`," +
          "`dateDownload`,`isLocal`,`libraryAddToken`,`libraryRemoveToken`,`lyricsOffset`," +
          "`romanizeLyrics`,`isDownloaded`,`isUploaded`,`isVideo`) " +
          "SELECT `id`,`title`,`duration`,`thumbnailUrl`,`albumId`,`albumName`,`explicit`,`year`," +
          "`date`,`dateModified`,`liked`,`likedDate`,`totalPlayTime`,`inLibrary`,`dateDownload`," +
          "`isLocal`,`libraryAddToken`,`libraryRemoveToken`,$lyricsOffset,`romanizeLyrics`," +
          "`isDownloaded`,`isUploaded`,$isVideo FROM `song`",
      )
      db.execSQL("DROP TABLE `song`")
      db.execSQL("ALTER TABLE `_new_song` RENAME TO `song`")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_albumId` ON `song` (`albumId`)")

      db.execSQL(
        "CREATE TABLE IF NOT EXISTS `_new_artist` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
          "`thumbnailUrl` TEXT, `channelId` TEXT, `lastUpdateTime` INTEGER NOT NULL, " +
          "`bookmarkedAt` INTEGER, `isLocal` INTEGER NOT NULL DEFAULT false, PRIMARY KEY(`id`))",
      )
      db.execSQL(
        "INSERT INTO `_new_artist` (`id`,`name`,`thumbnailUrl`,`channelId`,`lastUpdateTime`," +
          "`bookmarkedAt`,`isLocal`) SELECT `id`,`name`,`thumbnailUrl`,`channelId`," +
          "`lastUpdateTime`,`bookmarkedAt`,`isLocal` FROM `artist`",
      )
      db.execSQL("DROP TABLE `artist`")
      db.execSQL("ALTER TABLE `_new_artist` RENAME TO `artist`")

      db.execSQL(
        "CREATE TABLE IF NOT EXISTS `_new_album` (`id` TEXT NOT NULL, `playlistId` TEXT, " +
          "`title` TEXT NOT NULL, `year` INTEGER, `thumbnailUrl` TEXT, `themeColor` INTEGER, " +
          "`songCount` INTEGER NOT NULL, `duration` INTEGER NOT NULL, " +
          "`explicit` INTEGER NOT NULL DEFAULT 0, `lastUpdateTime` INTEGER NOT NULL, " +
          "`bookmarkedAt` INTEGER, `likedDate` INTEGER, `inLibrary` INTEGER, " +
          "`isLocal` INTEGER NOT NULL DEFAULT false, " +
          "`isUploaded` INTEGER NOT NULL DEFAULT false, PRIMARY KEY(`id`))",
      )
      db.execSQL(
        "INSERT INTO `_new_album` (`id`,`playlistId`,`title`,`year`,`thumbnailUrl`,`themeColor`," +
          "`songCount`,`duration`,`explicit`,`lastUpdateTime`,`bookmarkedAt`,`likedDate`," +
          "`inLibrary`,`isLocal`,`isUploaded`) " +
          "SELECT `id`,`playlistId`,`title`,`year`,`thumbnailUrl`,`themeColor`,`songCount`," +
          "`duration`,`explicit`,`lastUpdateTime`,`bookmarkedAt`,`likedDate`,`inLibrary`," +
          "`isLocal`,`isUploaded` FROM `album`",
      )
      db.execSQL("DROP TABLE `album`")
      db.execSQL("ALTER TABLE `_new_album` RENAME TO `album`")

      db.execSQL(
        "CREATE TABLE IF NOT EXISTS `_new_playlist` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
          "`browseId` TEXT, `createdAt` INTEGER, `lastUpdateTime` INTEGER, " +
          "`isEditable` INTEGER NOT NULL DEFAULT true, `bookmarkedAt` INTEGER, " +
          "`remoteSongCount` INTEGER, `playEndpointParams` TEXT, `thumbnailUrl` TEXT, " +
          "`shuffleEndpointParams` TEXT, `radioEndpointParams` TEXT, " +
          "`isLocal` INTEGER NOT NULL DEFAULT false, " +
          "`isAutoSync` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))",
      )
      db.execSQL(
        "INSERT INTO `_new_playlist` (`id`,`name`,`browseId`,`createdAt`,`lastUpdateTime`," +
          "`isEditable`,`bookmarkedAt`,`remoteSongCount`,`playEndpointParams`,`thumbnailUrl`," +
          "`shuffleEndpointParams`,`radioEndpointParams`,`isLocal`) " +
          "SELECT `id`,`name`,`browseId`,`createdAt`,`lastUpdateTime`,`isEditable`,`bookmarkedAt`," +
          "`remoteSongCount`,`playEndpointParams`,`thumbnailUrl`,`shuffleEndpointParams`," +
          "`radioEndpointParams`,`isLocal` FROM `playlist`",
      )
      db.execSQL("UPDATE `_new_playlist` SET `isAutoSync` = 0")
      db.execSQL("DROP TABLE `playlist`")
      db.execSQL("ALTER TABLE `_new_playlist` RENAME TO `playlist`")

      createViews(db)
    }
  }

val MIGRATION_28_29 =
  object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
      dropViews(db)
      if (!hasColumn(db, "playlist", "isAutoSync")) {
        db.execSQL("ALTER TABLE `playlist` ADD COLUMN `isAutoSync` INTEGER NOT NULL DEFAULT false")
      }
      createViews(db)
    }
  }

val MIGRATION_29_30 =
  object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
      dropViews(db)
      addColumnIfMissing(db, "song", "isVideo", "INTEGER NOT NULL DEFAULT 0")
      addColumnIfMissing(db, "lyrics", "provider", "TEXT NOT NULL DEFAULT 'Unknown'")
      createViews(db)
    }
  }

val MIGRATION_31_32 =
  object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
      addColumnIfMissing(db, "lyrics", "provider", "TEXT NOT NULL DEFAULT 'Unknown'")
      addColumnIfMissing(db, "lyrics", "translatedLyrics", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "lyrics", "translationLanguage", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "lyrics", "translationMode", "TEXT NOT NULL DEFAULT ''")
    }
  }

val MIGRATION_36_37 =
  object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
      // Intentionally empty: keeps downgrades from v37 from crashing on a missing migration.
    }
  }

val MIGRATION_37_38 =
  object : Migration(37, 38) {
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
      addColumnIfMissing(db, "format", "perceptualLoudnessDb", "REAL DEFAULT NULL")
    }
  }

val MIGRATION_38_39 =
  object : Migration(38, 39) {
    override fun migrate(db: SupportSQLiteDatabase) {
      addColumnIfMissing(db, "format", "perceptualLoudnessDb", "REAL DEFAULT NULL")
    }
  }

val MIGRATION_39_40 =
  object : Migration(39, 40) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `beat_info` (
          `songId` TEXT NOT NULL,
          `bpm` REAL NOT NULL,
          `firstBeatOffsetMs` INTEGER NOT NULL,
          `confidence` REAL NOT NULL,
          `analyzedAt` INTEGER NOT NULL,
          PRIMARY KEY(`songId`)
        )
        """
          .trimIndent(),
      )
    }
  }

val MIGRATION_40_41 =
  object : Migration(40, 41) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("ALTER TABLE beat_info ADD COLUMN mixInPointMs INTEGER DEFAULT NULL")
      db.execSQL("ALTER TABLE beat_info ADD COLUMN mixOutPointMs INTEGER DEFAULT NULL")
    }
  }

val MIGRATION_41_42 =
  object : Migration(41, 42) {
    override fun migrate(db: SupportSQLiteDatabase) {
      // The legacy tables are removed by the paired AutoMigration spec.
    }
  }

val MIGRATION_42_43 =
  object : Migration(42, 43) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL(
        "CREATE TABLE IF NOT EXISTS `_new_playlist` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
          "`browseId` TEXT, `createdAt` INTEGER, `lastUpdateTime` INTEGER, " +
          "`isEditable` INTEGER NOT NULL DEFAULT true, `bookmarkedAt` INTEGER, " +
          "`remoteSongCount` INTEGER, `playEndpointParams` TEXT, `thumbnailUrl` TEXT, " +
          "`shuffleEndpointParams` TEXT, `radioEndpointParams` TEXT, " +
          "`isLocal` INTEGER NOT NULL DEFAULT false, " +
          "`isAutoSync` INTEGER NOT NULL DEFAULT false, " +
          "`isPinned` INTEGER NOT NULL DEFAULT false, PRIMARY KEY(`id`))",
      )
      db.execSQL(
        "INSERT INTO `_new_playlist` (`id`,`name`,`browseId`,`createdAt`,`lastUpdateTime`," +
          "`isEditable`,`bookmarkedAt`,`remoteSongCount`,`playEndpointParams`,`thumbnailUrl`," +
          "`shuffleEndpointParams`,`radioEndpointParams`,`isLocal`,`isAutoSync`,`isPinned`) " +
          "SELECT `id`,`name`,`browseId`,`createdAt`,`lastUpdateTime`,`isEditable`," +
          "`bookmarkedAt`,`remoteSongCount`,`playEndpointParams`,`thumbnailUrl`," +
          "`shuffleEndpointParams`,`radioEndpointParams`,`isLocal`,`isAutoSync`,`isPinned` " +
          "FROM `playlist`",
      )
      db.execSQL("DROP TABLE `playlist`")
      db.execSQL("ALTER TABLE `_new_playlist` RENAME TO `playlist`")
    }
  }

val MIGRATION_43_44 =
  object : Migration(43, 44) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("ALTER TABLE beat_info ADD COLUMN keyPitchClass INTEGER DEFAULT NULL")
      db.execSQL("ALTER TABLE beat_info ADD COLUMN keyIsMinor INTEGER DEFAULT NULL")
    }
  }

/**
 * Safety net for databases that already had `isLocal` from an older fork schema: only add the
 * column when it is truly absent, so a duplicate-column ALTER cannot crash the upgrade.
 */
val MIGRATION_44_45 =
  object : Migration(44, 45) {
    override fun migrate(db: SupportSQLiteDatabase) {
      listOf("song", "artist", "playlist").forEach { table ->
        if (!hasColumn(db, table, "isLocal")) {
          db.execSQL("ALTER TABLE `$table` ADD COLUMN `isLocal` INTEGER NOT NULL DEFAULT 0")
          Timber.tag("MIGRATION_44_45").i("Added missing isLocal column to $table")
        }
      }
    }
  }

val MIGRATION_45_46 =
  object : Migration(45, 46) {
    override fun migrate(db: SupportSQLiteDatabase) {
      if (!hasColumn(db, "song", "hideFromQuickPicks")) {
        db.execSQL("ALTER TABLE `song` ADD COLUMN `hideFromQuickPicks` INTEGER NOT NULL DEFAULT 0")
      }
    }
  }

// ---------------------------------------------------------------------------------------------
// Auto-migration specs
// ---------------------------------------------------------------------------------------------

private fun addColumnIfMissing(
  db: SupportSQLiteDatabase,
  table: String,
  column: String,
  definition: String,
) {
  if (hasColumn(db, table, column)) return
  try {
    db.execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $definition")
  } catch (e: Exception) {
    Timber.tag("Migration").w(e, "Column $table.$column may already exist")
  }
}

private fun dropViews(db: SupportSQLiteDatabase) {
  db.execSQL("DROP VIEW IF EXISTS sorted_song_artist_map")
  db.execSQL("DROP VIEW IF EXISTS sorted_song_album_map")
  db.execSQL("DROP VIEW IF EXISTS playlist_song_map_preview")
}

private fun createViews(db: SupportSQLiteDatabase) {
  db.execSQL("CREATE VIEW `sorted_song_artist_map` AS SELECT * FROM song_artist_map ORDER BY position")
  db.execSQL("CREATE VIEW `sorted_song_album_map` AS SELECT * FROM song_album_map ORDER BY `index`")
  db.execSQL(
    "CREATE VIEW `playlist_song_map_preview` AS " +
      "SELECT * FROM playlist_song_map WHERE position <= 3 ORDER BY position",
  )
}

@DeleteColumn.Entries(
  DeleteColumn(tableName = "song", columnName = "isTrash"),
  DeleteColumn(tableName = "playlist", columnName = "author"),
  DeleteColumn(tableName = "playlist", columnName = "authorId"),
  DeleteColumn(tableName = "playlist", columnName = "year"),
  DeleteColumn(tableName = "playlist", columnName = "thumbnailUrl"),
  DeleteColumn(tableName = "playlist", columnName = "createDate"),
  DeleteColumn(tableName = "playlist", columnName = "lastUpdateTime"),
)
@RenameColumn.Entries(
  RenameColumn(tableName = "song", fromColumnName = "download_state", toColumnName = "downloadState"),
  RenameColumn(tableName = "song", fromColumnName = "create_date", toColumnName = "createDate"),
  RenameColumn(tableName = "song", fromColumnName = "modify_date", toColumnName = "modifyDate"),
)
class Migration5To6 : AutoMigrationSpec {
  override fun onPostMigrate(db: SupportSQLiteDatabase) {
    db.query("SELECT id FROM playlist WHERE id NOT LIKE 'LP%'").use { cursor ->
      while (cursor.moveToNext()) {
        val id = cursor.getString(0)
        db.execSQL("UPDATE playlist SET browseId = ? WHERE id = ?", arrayOf<Any>(id, id))
      }
    }
  }
}

class Migration6To7 : AutoMigrationSpec {
  override fun onPostMigrate(db: SupportSQLiteDatabase) {
    db.query("SELECT id, createDate FROM song").use { cursor ->
      while (cursor.moveToNext()) {
        db.execSQL(
          "UPDATE song SET inLibrary = ? WHERE id = ?",
          arrayOf<Any>(cursor.getLong(1), cursor.getString(0)),
        )
      }
    }
  }
}

@DeleteColumn.Entries(
  DeleteColumn(tableName = "song", columnName = "createDate"),
  DeleteColumn(tableName = "song", columnName = "modifyDate"),
)
class Migration7To8 : AutoMigrationSpec

@DeleteTable.Entries(DeleteTable(tableName = "download"))
class Migration9To10 : AutoMigrationSpec

@DeleteColumn.Entries(
  DeleteColumn(tableName = "song", columnName = "downloadState"),
  DeleteColumn(tableName = "artist", columnName = "bannerUrl"),
  DeleteColumn(tableName = "artist", columnName = "description"),
  DeleteColumn(tableName = "artist", columnName = "createDate"),
)
class Migration10To11 : AutoMigrationSpec

@DeleteColumn.Entries(DeleteColumn(tableName = "album", columnName = "createDate"))
class Migration11To12 : AutoMigrationSpec {
  override fun onPostMigrate(db: SupportSQLiteDatabase) {
    db.execSQL("UPDATE album SET bookmarkedAt = lastUpdateTime")
    db.query("SELECT DISTINCT albumId, albumName FROM song").use { cursor ->
      while (cursor.moveToNext()) {
        db.insert(
          table = "album",
          conflictAlgorithm = SQLiteDatabase.CONFLICT_IGNORE,
          values =
            contentValuesOf(
              "id" to cursor.getString(0),
              "title" to cursor.getString(1),
              "songCount" to 0,
              "duration" to 0,
              "lastUpdateTime" to 0,
            ),
        )
      }
    }
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_albumId` ON `song` (`albumId`)")
  }
}

class Migration12To13 : AutoMigrationSpec

class Migration13To14 : AutoMigrationSpec {
  @SuppressLint("Range")
  override fun onPostMigrate(db: SupportSQLiteDatabase) {
    val now = Converters().dateToTimestamp(LocalDateTime.now())
    db.execSQL("UPDATE playlist SET createdAt = ?", arrayOf<Any>(now!!))
    db.execSQL("UPDATE playlist SET lastUpdateTime = ?", arrayOf<Any>(now))
  }
}

@DeleteColumn.Entries(
  DeleteColumn(tableName = "song", columnName = "isLocal"),
  DeleteColumn(tableName = "song", columnName = "localPath"),
  DeleteColumn(tableName = "artist", columnName = "isLocal"),
  DeleteColumn(tableName = "playlist", columnName = "isLocal"),
)
class Migration16To17 : AutoMigrationSpec {
  override fun onPostMigrate(db: SupportSQLiteDatabase) {
    db.execSQL("UPDATE playlist SET bookmarkedAt = lastUpdateTime")
    db.execSQL("UPDATE playlist SET isEditable = 1 WHERE browseId IS NOT NULL")
  }
}

class Migration18To19 : AutoMigrationSpec {
  override fun onPostMigrate(db: SupportSQLiteDatabase) {
    db.execSQL("UPDATE song SET explicit = 0 WHERE explicit IS NULL")
  }
}

class Migration19To20 : AutoMigrationSpec {
  override fun onPostMigrate(db: SupportSQLiteDatabase) {
    db.execSQL("UPDATE song SET explicit = 0 WHERE explicit IS NULL")
  }
}

@DeleteColumn.Entries(DeleteColumn(tableName = "song", columnName = "artistName"))
class Migration20To21 : AutoMigrationSpec

class Migration21To22 : AutoMigrationSpec {
  override fun onPostMigrate(db: SupportSQLiteDatabase) {
    addColumnIfMissing(db, "song", "libraryAddToken", "TEXT DEFAULT ''")
    addColumnIfMissing(db, "song", "libraryRemoveToken", "TEXT DEFAULT ''")
    addColumnIfMissing(db, "song", "romanizeLyrics", "INTEGER NOT NULL DEFAULT 1")
    addColumnIfMissing(db, "song", "isDownloaded", "INTEGER NOT NULL DEFAULT 0")
  }
}

class Migration22To23 : AutoMigrationSpec

class Migration23To24 : AutoMigrationSpec {
  override fun onPostMigrate(db: SupportSQLiteDatabase) {
    addColumnIfMissing(db, "song", "isUploaded", "INTEGER NOT NULL DEFAULT 0")
  }
}

class Migration36To37Spec : AutoMigrationSpec

@DeleteTable.Entries(
  DeleteTable(tableName = "brain_activity_log"),
  DeleteTable(tableName = "play_event"),
  DeleteTable(tableName = "taste_profile"),
)
class Migration41To42 : AutoMigrationSpec

class Migration46To47Spec : AutoMigrationSpec {
  override fun onPostMigrate(db: SupportSQLiteDatabase) {
    // Seed per-track stats from the existing listen history so scores survive the upgrade.
    db.execSQL(
      """
      INSERT INTO song_play_stats (trackKey, title, artist, videoId, artworkUrl,
                                   totalPlayTimeMs, playCount, skipCount, lastPlayedAtMillis)
      SELECT
        lower(trim(s.title) || '|' || trim(ifnull(x.artist, ''))),
        s.title,
        ifnull(x.artist, ''),
        s.id,
        s.thumbnailUrl,
        sum(e.playTime),
        count(*),
        0,
        max(e.timestamp)
      FROM event e
      JOIN song s ON s.id = e.songId
      LEFT JOIN (
        SELECT songId, ifnull(group_concat(name, ', '), '') AS artist FROM (
          SELECT sam.songId AS songId, a.name AS name
          FROM song_artist_map sam
          LEFT JOIN artist a ON a.id = sam.artistId
          ORDER BY sam.songId, sam.position
        ) GROUP BY songId
      ) x ON x.songId = s.id
      WHERE e.playTime > 0
      GROUP BY lower(trim(s.title) || '|' || trim(ifnull(x.artist, '')))
      """
        .trimIndent(),
    )
  }
}

class Migration47To48Spec : AutoMigrationSpec {
  override fun onPostMigrate(db: SupportSQLiteDatabase) {
    // Older builds created a legacy taste_profile table; rebuild it for TasteProfileEntity.
    var needsRebuild = false
    db.query("PRAGMA table_info(taste_profile)").use { cursor ->
      val nameIndex = cursor.getColumnIndex("name")
      val columns = mutableSetOf<String>()
      while (cursor.moveToNext()) {
        if (nameIndex >= 0) columns += cursor.getString(nameIndex)
      }
      if ("genres" in columns || "topArtistsJson" !in columns) {
        needsRebuild = true
      }
    }
    if (needsRebuild) {
      db.execSQL("DROP TABLE IF EXISTS `taste_profile`")
      db.execSQL(
        "CREATE TABLE IF NOT EXISTS `taste_profile` (`id` INTEGER NOT NULL, " +
          "`topArtistsJson` TEXT NOT NULL, `topTracksJson` TEXT NOT NULL, " +
          "`topGenresJson` TEXT NOT NULL, `confidence` REAL NOT NULL, " +
          "`updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))",
      )
    }
  }
}
