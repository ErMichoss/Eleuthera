package com.tuapp.eleuthera.ui.update

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tuapp.eleuthera.data.update.AvailableUpdate
import com.tuapp.eleuthera.data.update.UpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class UpdateViewModel @Inject constructor(private val checker: UpdateChecker) : ViewModel() {
    val available: StateFlow<AvailableUpdate?> = checker.available

    fun checkInBackground() {
        viewModelScope.launch { checker.checkInBackground() }
    }

    fun dismiss() = checker.dismiss()
    fun skip(update: AvailableUpdate) = checker.skip(update.version)
}

/**
 * Aviso de versión nueva. Va en la raíz de la app: comprueba al abrir (como mucho cada 12 h)
 * y también aparece cuando el usuario busca a mano desde Ajustes > Acerca de.
 */
@Composable
fun UpdatePrompt(vm: UpdateViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { vm.checkInBackground() }

    val update by vm.available.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    val current = update ?: return

    AlertDialog(
        onDismissRequest = vm::dismiss,
        title = { Text("Versión ${current.version} disponible") },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text(
                    current.notes.ifBlank { "Hay una versión nueva de Eleuthera." },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Se descargará el APK; ábrelo para instalarlo encima. No perderás nada.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                uri.openUri(current.downloadUrl)
                vm.dismiss()
            }) { Text("Descargar") }
        },
        dismissButton = {
            Column {
                TextButton(onClick = vm::dismiss) { Text("Más tarde") }
                TextButton(onClick = { vm.skip(current) }) { Text("Omitir esta versión") }
            }
        },
    )
}