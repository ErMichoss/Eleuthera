package com.tuapp.eleuthera.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tuapp.eleuthera.platform.DesktopPaths
import kotlinx.coroutines.launch

/**
 * Carpetas que lee la biblioteca: la de descargas fija y las que añada el usuario.
 * Equivale a Ajustes > Biblioteca de Android; se moverá allí con los ajustes (fase 5c).
 */
@Composable
fun FoldersScreen(modifier: Modifier = Modifier) {
    val settings = AppGraph.librarySettings
    val extra by settings.extraFolders.collectAsState(emptyList())
    val pendingTags by AppGraph.library.pendingTags.collectAsState()
    val scope = rememberCoroutineScope()

    Column(modifier.padding(24.dp).widthIn(max = 720.dp)) {
        Text("Carpetas de música", style = MaterialTheme.typography.headlineSmall)
        Text(
            "La biblioteca muestra las canciones de estas carpetas y sus subcarpetas: " +
                    "mp3, m4a, webm, opus, ogg, flac y wav.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (pendingTags > 0) {
            Text(
                "Leyendo etiquetas: faltan $pendingTags canciones.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(16.dp))
        FolderRow(DesktopPaths.musicDir.path, "Descargas", onRemove = null)
        extra.forEach { dir ->
            FolderRow(dir.path, dir.name.ifEmpty { dir.path }) { scope.launch { settings.remove(dir) } }
        }
        Spacer(Modifier.height(8.dp))
        Row {
            TextButton(onClick = {
                FolderPicker.choose()?.let { dir -> scope.launch { settings.add(dir) } }
            }) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Añadir carpeta")
            }
            TextButton(onClick = AppGraph.library::refresh) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Buscar canciones nuevas")
            }
        }
    }
}

@Composable
private fun FolderRow(path: String, label: String, onRemove: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                path, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) { Icon(Icons.Default.Close, contentDescription = "Quitar carpeta") }
        } else {
            Spacer(Modifier.height(48.dp))
        }
    }
}