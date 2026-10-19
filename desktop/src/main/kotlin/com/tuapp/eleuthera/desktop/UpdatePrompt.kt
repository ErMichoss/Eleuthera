package com.tuapp.eleuthera.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.tuapp.eleuthera.platform.DesktopPaths
import kotlinx.coroutines.launch

/**
 * Aviso de versión nueva, como en Android. Comprueba al abrir (como mucho cada 12 h) y
 * también aparece cuando el usuario busca a mano desde Ajustes > Acerca de.
 */
@Composable
fun UpdatePrompt() {
    val checker = AppGraph.updates
    LaunchedEffect(Unit) { checker.checkInBackground() }

    val update by checker.available.collectAsState()
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val current = update ?: return

    val howTo = when {
        DesktopPaths.isWindows -> "Se descargará el instalador (.msi); ábrelo para instalar encima. No perderás nada."
        DesktopPaths.isMac -> "Se descargará el .dmg; arrastra Eleuthera a Aplicaciones para sustituir la anterior."
        else -> "Se descargará el .deb; instálalo encima (por ejemplo, con: sudo apt install ./archivo.deb)."
    }

    AlertDialog(
        onDismissRequest = checker::dismiss,
        title = { Text("Versión ${current.version} disponible") },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text(
                    current.notes.ifBlank { "Hay una versión nueva de Eleuthera." },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(howTo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                uri.openUri(current.downloadUrl)
                checker.dismiss()
            }) { Text("Descargar") }
        },
        dismissButton = {
            Column {
                TextButton(onClick = checker::dismiss) { Text("Más tarde") }
                TextButton(onClick = { scope.launch { checker.skip(current.version) } }) { Text("Omitir esta versión") }
            }
        },
    )
}
