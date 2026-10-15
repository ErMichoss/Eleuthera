package com.tuapp.eleuthera.ui.common

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tuapp.eleuthera.domain.model.Track

/** Una acción extra de la barra de selección (p. ej. "Quitar de esta playlist"). */
data class SelectionAction(val label: String, val destructive: Boolean = false, val run: (List<Track>) -> Unit)

/**
 * Barra que sustituye a la cabecera mientras hay canciones seleccionadas.
 * [selected] son las canciones marcadas, en el orden de la lista.
 */
@Composable
fun SelectionBar(
    selected: List<Track>,
    total: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onPlayNext: (List<Track>) -> Unit,
    onAddToQueue: (List<Track>) -> Unit,
    onAddToPlaylist: (List<Track>) -> Unit,
    extraActions: List<SelectionAction> = emptyList(),
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }

    /** Hace la acción, avisa con [message] y sale de la selección. */
    fun done(message: String? = null, action: () -> Unit) {
        menu = false
        action()
        message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        onClose()
    }

    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Salir de la selección")
            }
            Text(
                "${selected.size} seleccionadas",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (selected.size < total) {
                IconButton(onClick = onSelectAll) {
                    Icon(Icons.Default.SelectAll, contentDescription = "Seleccionar todas")
                }
            } else {
                IconButton(onClick = onClearAll) {
                    Icon(Icons.Default.Deselect, contentDescription = "Quitar selección")
                }
            }
            IconButton(
                onClick = { done { onAddToPlaylist(selected) } },
                enabled = selected.isNotEmpty(),
            ) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Añadir a playlist")
            }
            Box {
                IconButton(onClick = { menu = true }, enabled = selected.isNotEmpty()) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Reproducir a continuación") },
                        onClick = {
                            done("${selected.size} canciones sonarán a continuación") { onPlayNext(selected) }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Añadir a la cola") },
                        onClick = {
                            done("${selected.size} canciones añadidas a la cola") { onAddToQueue(selected) }
                        },
                    )
                    if (extraActions.isNotEmpty()) HorizontalDivider()
                    extraActions.forEach { extra ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    extra.label,
                                    color = if (extra.destructive) MaterialTheme.colorScheme.error
                                    else Color.Unspecified,
                                )
                            },
                            onClick = { done { extra.run(selected) } },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Toque y pulsación larga de una fila de canción:
 * - sin selección: tocar reproduce; mantener pulsado empieza a seleccionar.
 * - con selección: tocar marca o desmarca.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.trackRowClicks(
    selecting: Boolean,
    onPlay: () -> Unit,
    onToggle: () -> Unit,
): Modifier = combinedClickable(
    onClick = { if (selecting) onToggle() else onPlay() },
    onLongClick = onToggle,
)

/** Colores de la fila: marcada > sonando > normal. */
@Composable
fun selectableRowColors(
    track: Track,
    isSelected: Boolean,
    base: Color = MaterialTheme.colorScheme.surface,
): ListItemColors =
    if (isSelected) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    else trackRowColors(track, base)