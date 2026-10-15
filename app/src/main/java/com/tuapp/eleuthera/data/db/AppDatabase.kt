package com.tuapp.eleuthera.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        VideoTrackEntity::class,
        PlaylistRequestEntity::class,
        FolderTrackEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun downloadDao(): DownloadDao
    abstract fun folderTrackDao(): FolderTrackDao

    companion object {
        /** v2: portada personalizada. Conserva todas las playlists existentes. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN coverPath TEXT")
            }
        }

        /** v3: evitar descargas duplicadas entre playlists. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `video_tracks` (`videoId` TEXT NOT NULL, " +
                            "`trackId` INTEGER NOT NULL, PRIMARY KEY(`videoId`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlist_requests` (`videoId` TEXT NOT NULL, " +
                            "`playlistName` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`videoId`, `playlistName`))"
                )
            }
        }

        /** v4: etiquetas de las canciones de carpetas añadidas en Ajustes. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `folder_tracks` (`documentId` TEXT NOT NULL, " +
                            "`uri` TEXT NOT NULL, `folder` TEXT NOT NULL, " +
                            "`lastModified` INTEGER NOT NULL, `size` INTEGER NOT NULL, " +
                            "`title` TEXT NOT NULL, `artist` TEXT, `album` TEXT, " +
                            "`durationMs` INTEGER NOT NULL, `tagged` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`documentId`))"
                )
            }
        }
    }
}