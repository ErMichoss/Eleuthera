package com.tuapp.eleuthera.ui.library

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.tuapp.eleuthera.domain.model.Track
import com.tuapp.eleuthera.ui.lyrics.LyricsViewModel
import kotlinx.coroutines.launch

private val audioPermission =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

/**
 * Biblioteca en Android: lo propio de Android (permiso de lectura y la confirmación del
 * sistema al borrar) alrededor de la pantalla común [LibraryContent].
 */
@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    actions: TrackActions = TrackActions(),
    vm: LibraryViewModel = hiltViewModel(),
    lyricsVm: LyricsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    LaunchedEffect(granted) { if (granted) vm.start() else launcher.launch(audioPermission) }

    if (!granted) {
        PermissionPrompt(modifier) { launcher.launch(audioPermission) }
        return
    }

    val scope = rememberCoroutineScope()
    val folderLyricsProgress by lyricsVm.batchProgress.collectAsState()
    var awaitingConfirm by remember { mutableStateOf<Track?>(null) }
    val confirmLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val track = awaitingConfirm
        awaitingConfirm = null
        if (result.resultCode == Activity.RESULT_OK && track != null &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R
        ) {
            scope.launch { vm.delete(track) }
        }
    }

    LibraryContent(
        controller = vm.controller,
        actions = actions,
        modifier = modifier,
        onSearchFolderLyrics = lyricsVm::toggleFolderSearch,
        folderLyricsProgress = folderLyricsProgress,
        onConfirmDelete = { track ->
            scope.launch {
                val sender = vm.delete(track)
                if (sender != null) {
                    awaitingConfirm = track
                    confirmLauncher.launch(IntentSenderRequest.Builder(sender).build())
                }
            }
        },
    )
}

@Composable
private fun PermissionPrompt(modifier: Modifier, onRequest: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Eleuthera necesita permiso para leer la música del dispositivo.",
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRequest) { Text("Conceder permiso") }
        Text(
            "Si el botón no hace nada, actívalo en Ajustes > Aplicaciones > Eleuthera > Permisos.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
