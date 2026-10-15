package com.tuapp.eleuthera.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Ajustes > Biblioteca: carpetas que se muestran. */
@Composable
fun LibrarySettingsPage(vm: LibrarySettingsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    SettingsPageScaffold("Biblioteca", onBack, modifier) {
        SettingsHint(
            "La biblioteca muestra las canciones de estas carpetas y sus subcarpetas: " +
                    "mp3, m4a, webm, opus, ogg, flac y wav."
        )
        LibraryFoldersSection(vm)
        SectionTitle("Acceso rápido")
        QuickPlaylistsSwitch(vm)
    }
}

/** Activa el botón flotante de playlists rápidas. */
@Composable
private fun QuickPlaylistsSwitch(vm: LibrarySettingsViewModel) {
    val enabled by vm.quickPlaylists.collectAsStateWithLifecycle()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Playlists rápidas", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Muestra un botón flotante para reproducir o añadir a la cola cualquier playlist " +
                        "sin salir de la pantalla en la que estés.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = enabled, onCheckedChange = vm::setQuickPlaylists)
    }
}

/** Carpetas que muestra la biblioteca. */
@Composable
private fun LibraryFoldersSection(vm: LibrarySettingsViewModel) {
    val folders by vm.folders.collectAsStateWithLifecycle()
    val legacy by vm.legacyFolders.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.add(uri)
    }

    Column(Modifier.padding(horizontal = 16.dp)) {
        FolderRow("Music/Eleuthera (descargas)", onRemove = null)
        folders.forEach { folder ->
            FolderRow(folder.label) { vm.remove(folder) }
        }
        TextButton(onClick = { picker.launch(null) }) {
            Icon(Icons.Default.CreateNewFolder, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Añadir carpeta")
        }
        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        if (legacy.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Estas carpetas se añadieron con una versión anterior y ya no se leen. " +
                        "Quítalas y vuelve a añadirlas con «Añadir carpeta».",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            legacy.forEach { path ->
                FolderRow(
                    label = path.trimEnd('/').ifEmpty { "Almacenamiento completo" },
                    icon = Icons.Default.FolderOff,
                ) { vm.removeLegacy(path) }
            }
        }
    }
}

@Composable
private fun FolderRow(
    label: String,
    icon: ImageVector = Icons.Default.Folder,
    onRemove: (() -> Unit)?,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Quitar carpeta")
            }
        } else {
            Spacer(Modifier.height(48.dp))
        }
    }
}