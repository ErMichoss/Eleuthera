package com.tuapp.eleuthera.data.settings

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.libraryStore by preferencesDataStore(name = "library")

/**
 * Carpetas extra que la biblioteca incluye además de Música/Eleuthera.
 * Se guardan como rutas relativas de MediaStore, p. ej. "Music/Rock/" o "Download/".
 */
@Singleton
class LibrarySettings @Inject constructor(@ApplicationContext private val ctx: Context) {

    private val foldersKey = stringSetPreferencesKey("extra_folders")

    val folders: Flow<Set<String>> = ctx.libraryStore.data.map { it[foldersKey].orEmpty() }

    /**
     * Añade la carpeta elegida con el selector del sistema. Devuelve su ruta relativa,
     * o null si está en una tarjeta SD u otro volumen (MediaStore no la indexa igual).
     */
    suspend fun add(treeUri: Uri): String? {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)   // "primary:Music/Rock"
        if (!docId.startsWith("primary:")) return null
        val relative = docId.removePrefix("primary:").trim('/').let { if (it.isEmpty()) "" else "$it/" }
        ctx.libraryStore.edit { it[foldersKey] = it[foldersKey].orEmpty() + relative }
        return relative
    }

    suspend fun remove(relativePath: String) {
        ctx.libraryStore.edit { it[foldersKey] = it[foldersKey].orEmpty() - relativePath }
    }
}
