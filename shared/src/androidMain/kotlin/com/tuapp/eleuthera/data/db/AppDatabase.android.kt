package com.tuapp.eleuthera.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * La base de datos de Android, en el mismo sitio de siempre (databases/eleuthera.db):
 * al actualizar la app se conservan las playlists y las migraciones la ponen al día.
 */
fun buildAppDatabase(context: Context): AppDatabase {
    val file = context.applicationContext.getDatabasePath(AppDatabase.FILE_NAME)
    return Room.databaseBuilder<AppDatabase>(context.applicationContext, file.absolutePath)
        // El SQLite del propio Android; las migraciones comunes usan este "driver".
        .setDriver(AndroidSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addMigrations(*AppDatabase.MIGRATIONS)
        .build()
}