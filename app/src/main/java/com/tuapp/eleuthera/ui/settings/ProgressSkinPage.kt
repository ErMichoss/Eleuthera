package com.tuapp.eleuthera.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** Ajustes > Barra de progreso en Android: la página común con el selector de fotos del sistema. */
@Composable
fun ProgressSkinPage(vm: ProgressSkinViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var onPicked by remember { mutableStateOf<((Uri) -> Unit)?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPicked?.invoke(uri)
        onPicked = null
    }
    ProgressSkinSettingsPage(
        vm = vm.controller,
        pickImage = { callback ->
            onPicked = callback
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onBack = onBack,
        modifier = modifier,
    )
}