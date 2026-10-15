package com.tuapp.eleuthera.data.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.libraryStore by preferencesDataStore(name = "library")

/** Carpeta añadida en Ajustes: la URI del selector (con permiso guardado) y su nombre visible. */
data class LibraryFolder(val treeUri: Uri, val label: String)

/**
 * Carpetas extra que la biblioteca incluye además de Música/Eleuthera.
 * Se recorren nosotros mismos (no MediaStore), así que entra todo archivo con extensión
 * de audio aunque Android lo tenga clasificado como vídeo o no lo haya indexado.
 */
@Singleton
class LibrarySettings @Inject constructor(@ApplicationContext private val ctx: Context) {

    private val treesKey = stringSetPreferencesKey("folder_trees")
    private val quickPlaylistsKey = booleanPreferencesKey("quick_playlists")

    /** Botón flotante con tus playlists para reproducirlas o encolarlas desde cualquier pantalla. */
    val quickPlaylists: Flow<Boolean> = ctx.libraryStore.data.map { it[quickPlaylistsKey] ?: false }

    suspend fun setQuickPlaylists(enabled: Boolean) {
        ctx.libraryStore.edit { it[quickPlaylistsKey] = enabled }
    }

    private val quickXKey = floatPreferencesKey("quick_button_x")
    private val quickYKey = floatPreferencesKey("quick_button_y")

    /**
     * Dónde dejó el usuario el botón flotante, en fracciones del área disponible
     * (0,0 = arriba a la izquierda; 1,1 = abajo a la derecha). null = posición por defecto.
     */
    val quickButtonPosition: Flow<Pair<Float, Float>?> = ctx.libraryStore.data.map { p ->
        val x = p[quickXKey]
        val y = p[quickYKey]
        if (x != null && y != null) x to y else null
    }

    suspend fun setQuickButtonPosition(x: Float, y: Float) {
        ctx.libraryStore.edit {
            it[quickXKey] = x
            it[quickYKey] = y
        }
    }

    /** Formato antiguo (solo la ruta, sin permiso). Se muestran para que el usuario las vuelva a añadir. */
    private val legacyKey = stringSetPreferencesKey("extra_folders")

    val folders: Flow<List<LibraryFolder>> = ctx.libraryStore.data.map { prefs ->
        prefs[treesKey].orEmpty()
            .map { Uri.parse(it) }
            .map { LibraryFolder(it, labelOf(DocumentsContract.getTreeDocumentId(it))) }
            .sortedBy { it.label.lowercase() }
    }

    val legacyFolders: Flow<List<String>> =
        ctx.libraryStore.data.map { it[legacyKey].orEmpty().sorted() }

    /** Guarda la carpeta elegida y su permiso para siempre. false si Android no lo concede. */
    suspend fun add(treeUri: Uri): Boolean {
        val resolver = ctx.contentResolver
        val granted = runCatching {
            resolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.recoverCatching {
            // Algunos proveedores solo dan lectura: sin escritura no se podrá borrar desde la app.
            resolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.isSuccess
        if (!granted) return false
        ctx.libraryStore.edit { it[treesKey] = it[treesKey].orEmpty() + treeUri.toString() }
        return true
    }

    suspend fun remove(folder: LibraryFolder) {
        ctx.libraryStore.edit { it[treesKey] = it[treesKey].orEmpty() - folder.treeUri.toString() }
        runCatching {
            ctx.contentResolver.releasePersistableUriPermission(
                folder.treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    suspend fun removeLegacy(relativePath: String) {
        ctx.libraryStore.edit { it[legacyKey] = it[legacyKey].orEmpty() - relativePath }
    }

    companion object {
        /**
         * Nombre legible de un documento: "primary:Download/Musica" -> "Download/Musica",
         * "1A2B-3C4D:Musica" -> "SD/Musica", "primary:" -> "Almacenamiento interno".
         */
        fun labelOf(documentId: String): String {
            val volume = documentId.substringBefore(':')
            val path = documentId.substringAfter(':', "").trim('/')
            return when {
                volume == "primary" -> path.ifEmpty { "Almacenamiento interno" }
                path.isEmpty() -> "SD"
                else -> "SD/$path"
            }
        }
    }
}