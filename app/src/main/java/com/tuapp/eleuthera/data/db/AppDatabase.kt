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
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun downloadDao(): DownloadDao

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
    }
}