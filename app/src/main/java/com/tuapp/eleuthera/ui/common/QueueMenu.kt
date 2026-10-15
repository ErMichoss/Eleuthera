package com.tuapp.eleuthera.ui.common

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import com.tuapp.eleuthera.domain.model.Track
import kotlinx.coroutines.launch

/**
 * Botón para mandar una lista entera (playlist, álbum, artista, carpeta) a la cola.
 * [loadTracks] es suspend para poder usarlo desde la cuadrícula de playlists, donde las
 * canciones aún no están cargadas.
 */
@Composable
fun QueueMenuButton(
    loadTracks: suspend () -> List<Track>,
    onPlayNext: (List<Track>) -> Unit,
    onAddToQueue: (List<Track>) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.AutoMirrored.Filled.QueueMusic,
) {
    var open by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun send(next: Boolean) {
        open = false
        scope.launch {
            val tracks = loadTracks()
            if (tracks.isEmpty()) return@launch
            if (next) onPlayNext(tracks) else onAddToQueue(tracks)
            val msg = if (next) "${tracks.size} canciones sonarán a continuación"
            else "${tracks.size} canciones añadidas a la cola"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier) {
        IconButton(onClick = { open = true }) {
            Icon(icon, contentDescription = "Añadir a la cola")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Reproducir a continuación") }, onClick = { send(next = true) })
            DropdownMenuItem(text = { Text("Añadir a la cola") }, onClick = { send(next = false) })
        }
    }
}