package com.tuapp.eleuthera.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tuapp.eleuthera.platform.DesktopPaths
import java.io.File
import kotlinx.coroutines.Dispatchers

/**
 * La base de datos de escritorio, en la carpeta de datos de la app (ver [DesktopPaths]).
 * SQLite va incluido en la app (BundledSQLiteDriver): no hay que instalar nada.
 */
fun buildAppDatabase(file: File = File(DesktopPaths.dataDir, AppDatabase.FILE_NAME)): AppDatabase {
    file.parentFile?.mkdirs()
    return Room.databaseBuilder<AppDatabase>(name = file.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addMigrations(*AppDatabase.MIGRATIONS)
        .build()
}