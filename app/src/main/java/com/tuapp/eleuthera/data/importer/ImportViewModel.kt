package com.tuapp.eleuthera.ui.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.eleuthera.data.importer.ImportFileParser
import com.tuapp.eleuthera.data.importer.ImportScheduler
import com.tuapp.eleuthera.data.importer.ImportStatus
import com.tuapp.eleuthera.domain.model.ImportedTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Archivo leído y pendiente de confirmar: playlist -> canciones. */
data class PendingImport(val groups: Map<String, List<ImportedTrack>>) {
    val total get() = groups.values.sumOf { it.size }
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val scheduler: ImportScheduler,
) : ViewModel() {

    private val _pending = MutableStateFlow<PendingImport?>(null)
    val pending: StateFlow<PendingImport?> = _pending.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Importaciones visibles; las cerradas por el usuario ya vienen filtradas. */
    val imports: StateFlow<List<ImportStatus>> = scheduler.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onFilePicked(uri: Uri) {
        viewModelScope.launch {
            _error.value = null
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = ctx.contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() }
                        ?: error("No se pudo leer el archivo")
                    val tracks = ImportFileParser.parse(text)
                    require(tracks.isNotEmpty()) { "No se encontraron canciones en el archivo" }
                    // Si el archivo trae varias playlists se respetan; si no, se usa su nombre.
                    val fallback = displayName(uri)?.substringBeforeLast('.') ?: "Importada"
                    tracks.groupBy { it.playlist ?: fallback }
                }
            }
            result.onSuccess { _pending.value = PendingImport(it) }
                .onFailure { _error.value = it.message }
        }
    }

    /** Confirma; [singleName] permite renombrar cuando el archivo es una sola playlist. */
    fun confirm(singleName: String?) {
        val pending = _pending.value ?: return
        _pending.value = null
        val groups = if (pending.groups.size == 1 && !singleName.isNullOrBlank()) {
            mapOf(singleName.trim() to pending.groups.values.first())
        } else pending.groups
        groups.forEach { (name, tracks) -> scheduler.enqueue(name, tracks) }
    }

    fun cancelPending() { _pending.value = null }
    fun clearError() { _error.value = null }
    fun cancel(status: ImportStatus) = scheduler.cancel(status.id)
    fun dismiss(status: ImportStatus) = scheduler.dismiss(status)

    suspend fun missingSongs(status: ImportStatus): List<String> = withContext(Dispatchers.IO) {
        status.missingFile?.let { File(it) }?.takeIf { it.exists() }
            ?.readLines()?.filter { it.isNotBlank() }.orEmpty()
    }

    private fun displayName(uri: Uri): String? =
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}