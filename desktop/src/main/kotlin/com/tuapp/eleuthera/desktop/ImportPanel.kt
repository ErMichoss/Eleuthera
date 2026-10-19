package com.tuapp.eleuthera.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tuapp.eleuthera.data.importer.DesktopImports
import com.tuapp.eleuthera.domain.model.ImportedTrack
import com.tuapp.eleuthera.ui.theme.AppCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Importar listas de Spotify, Qobuz… (CSV o TXT), debajo del buscador. Igual que el panel de
 * Android: se confirma el nombre de la playlist y se ve el progreso de cada importación.
 */
@Composable
fun ImportPanel(modifier: Modifier = Modifier) {
    val imports = AppGraph.imports
    val jobs by imports.jobs.collectAsState()
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Map<String, List<ImportedTrack>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        TextButton(onClick = {
            val file = FolderPicker.chooseFile("Elige una lista (CSV o TXT)", setOf("csv", "txt", "tsv"))
            if (file != null) {
                error = null
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) { imports.parse(file.readText(), file.nameWithoutExtension) }
                    }.onSuccess { pending = it }.onFailure { error = it.message }
                }
            }
        }) {
            Icon(Icons.Default.FileOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Importar lista de Spotify, Qobuz… (CSV o TXT)")
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        jobs.forEach { ImportCard(it, imports) }
    }

    pending?.let { groups ->
        var name by remember(groups) { mutableStateOf(groups.keys.first()) }
        val total = groups.values.sumOf { it.size }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Importar lista") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (groups.size == 1) {
                        Text("$total canciones. Se buscarán en YouTube y se descargarán en esta playlist:")
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            singleLine = true,
                            label = { Text("Nombre de la playlist") },
                        )
                    } else {
                        Text("$total canciones en ${groups.size} playlists:")
                        groups.forEach { (n, t) -> Text("• $n (${t.size})") }
                    }
                    Text(
                        "Puede tardar: unos segundos por canción. Deja la app abierta hasta que termine.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = groups.size > 1 || name.isNotBlank(),
                    onClick = {
                        val final = if (groups.size == 1) mapOf(name.trim() to groups.values.first()) else groups
                        final.forEach { (n, t) -> imports.enqueue(n, t) }
                        pending = null
                    },
                ) { Text("Importar") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ImportCard(job: DesktopImports.ImportJob, imports: DesktopImports) {
    var showMissing by remember { mutableStateOf(false) }

    AppCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(job.playlist, style = MaterialTheme.typography.titleSmall)
            if (!job.finished) {
                LinearProgressIndicator(
                    progress = { if (job.total > 0) job.done.toFloat() / job.total else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Buscando ${job.done}/${job.total}" +
                        if (job.missing.isNotEmpty()) " · ${job.missing.size} sin encontrar" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    "Listo: ${job.total - job.missing.size} en descarga, ${job.missing.size} sin encontrar.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (job.finished && job.missing.isNotEmpty()) {
                    TextButton(onClick = { showMissing = true }) { Text("Ver no encontradas") }
                }
                Spacer(Modifier.weight(1f))
                if (job.finished) TextButton(onClick = { imports.dismiss(job.id) }) { Text("Cerrar") }
                else TextButton(onClick = { imports.cancel(job.id) }) { Text("Cancelar") }
            }
        }
    }

    if (showMissing) {
        AlertDialog(
            onDismissRequest = { showMissing = false },
            title = { Text("No encontradas (${job.missing.size})") },
            text = {
                Column {
                    Text(
                        "Puedes buscarlas a mano en Buscar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(job.missing) { Text(it, modifier = Modifier.padding(vertical = 4.dp)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMissing = false }) { Text("Cerrar") } },
        )
    }
}
