package com.tuapp.eleuthera.data.settings

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.tuapp.eleuthera.platform.DesktopPaths
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Carpetas de música en escritorio. Como en Android, la de descargas (Música/Eleuthera)
 * siempre está y no se puede quitar; el usuario puede añadir las que quiera.
 */
class DesktopLibrarySettings(stores: PreferenceStores) {

    private val store = stores.get("library")
    private val foldersKey = stringSetPreferencesKey("folders")

    /** Las que ha añadido el usuario, por orden alfabético. */
    val extraFolders: Flow<List<File>> = store.data.map { prefs ->
        prefs[foldersKey].orEmpty().map(::File).sortedBy { it.path.lowercase() }
    }

    /** Todas las que se leen: primero la de descargas y luego las del usuario. */
    val folders: Flow<List<File>> = extraFolders.map { listOf(DesktopPaths.musicDir) + it }

    suspend fun add(dir: File) {
        val path = dir.absoluteFile.normalize().path
        if (File(path) == DesktopPaths.musicDir) return
        store.edit { it[foldersKey] = it[foldersKey].orEmpty() + path }
    }

    suspend fun remove(dir: File) {
        store.edit { it[foldersKey] = it[foldersKey].orEmpty() - dir.path }
    }
}