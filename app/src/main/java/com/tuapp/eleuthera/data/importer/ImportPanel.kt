package com.tuapp.eleuthera.ui.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuapp.eleuthera.data.importer.ImportStatus
import com.tuapp.eleuthera.ui.theme.AppCard
import kotlinx.coroutines.launch

/** Botón "Importar lista", confirmación y progreso de las importaciones. Va en Buscar. */
@Composable
fun ImportPanel(modifier: Modifier = Modifier, vm: ImportViewModel = hiltViewModel()) {
    val pending by vm.pending.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val imports by vm.imports.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.onFilePicked(uri)
    }

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        TextButton(onClick = { picker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) }) {
            Icon(Icons.Default.FileOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Importar lista de Spotify, Qobuz… (CSV o TXT)")
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        imports.forEach { status -> ImportCard(status, vm) }
    }

    pending?.let { p ->
        var name by remember(p) { mutableStateOf(p.groups.keys.first()) }
        AlertDialog(
            onDismissRequest = vm::cancelPending,
            title = { Text("Importar lista") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (p.groups.size == 1) {
                        Text("${p.total} canciones. Se buscarán en YouTube y se descargarán en esta playlist:")
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            singleLine = true,
                            label = { Text("Nombre de la playlist") },
                        )
                    } else {
                        Text("${p.total} canciones en ${p.groups.size} playlists:")
                        p.groups.forEach { (n, t) -> Text("• $n (${t.size})") }
                    }
                    Text(
                        "Puede tardar: unos segundos por canción, en segundo plano.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = p.groups.size > 1 || name.isNotBlank(),
                    onClick = { vm.confirm(name) },
                ) { Text("Importar") }
            },
            dismissButton = { TextButton(onClick = vm::cancelPending) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ImportCard(status: ImportStatus, vm: ImportViewModel) {
    val scope = rememberCoroutineScope()
    var missingList by remember { mutableStateOf<List<String>?>(null) }

    AppCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(status.playlist, style = MaterialTheme.typography.titleSmall)
            if (!status.finished) {
                LinearProgressIndicator(
                    progress = { if (status.total > 0) status.done.toFloat() / status.total else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Buscando ${status.done}/${status.total}" +
                            if (status.missing > 0) " · ${status.missing} sin encontrar" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    "Listo: ${status.total - status.missing} en descarga, ${status.missing} sin encontrar.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (status.finished && status.missing > 0) {
                    TextButton(onClick = { scope.launch { missingList = vm.missingSongs(status) } }) {
                        Text("Ver no encontradas")
                    }
                }
                Spacer(Modifier.weight(1f))
                if (status.finished) TextButton(onClick = { vm.dismiss(status) }) { Text("Cerrar") }
                else TextButton(onClick = { vm.cancel(status) }) { Text("Cancelar") }
            }
        }
    }

    missingList?.let { list ->
        AlertDialog(
            onDismissRequest = { missingList = null },
            title = { Text("No encontradas (${list.size})") },
            text = {
                Column {
                    Text(
                        "Puedes buscarlas a mano en Buscar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(list) { Text(it, modifier = Modifier.padding(vertical = 4.dp)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { missingList = null }) { Text("Cerrar") } },
        )
    }
}