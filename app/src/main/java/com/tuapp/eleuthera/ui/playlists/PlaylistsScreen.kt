package com.tuapp.eleuthera.ui.playlists

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
import androidx.hilt.navigation.compose.hiltViewModel
import com.tuapp.eleuthera.ui.library.TrackActions

/**
 * Playlists en Android: la pantalla común [PlaylistsContent] con el selector de fotos del
 * sistema para las portadas (no necesita permisos).
 */
@Composable
fun PlaylistsScreen(
    modifier: Modifier = Modifier,
    actions: TrackActions = TrackActions(),
    vm: PlaylistsViewModel = hiltViewModel(),
) {
    var onPicked by remember { mutableStateOf<((Uri) -> Unit)?>(null) }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onPicked?.invoke(uri)
        onPicked = null
    }

    PlaylistsContent(
        vm = vm.controller,
        actions = actions,
        modifier = modifier,
        pickImage = { callback ->
            onPicked = callback
            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
    )
}
