package com.tuapp.eleuthera.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.tuapp.eleuthera.data.settings.LoadedSkin
import com.tuapp.eleuthera.domain.model.ProgressSkin
import com.tuapp.eleuthera.domain.model.SkinSlot
import com.tuapp.eleuthera.domain.model.TrackMode
import com.tuapp.eleuthera.ui.player.SkinnedSeekBar
import java.io.File

/** Ajustes > Barra de progreso: elegir, crear y editar diseños. */
@Composable
fun ProgressSkinPage(vm: ProgressSkinViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    SettingsPageScaffold("Barra de progreso", onBack, modifier) {
        ProgressSkinSection(vm)
    }
}

@Composable
private fun ProgressSkinSection(vm: ProgressSkinViewModel) {
    val skins by vm.skins.collectAsStateWithLifecycle()
    val activeId by vm.activeId.collectAsStateWithLifecycle()
    val loaded by vm.active.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val active = skins.firstOrNull { it.id == activeId }

    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    Preview(loaded)

    // Diseños guardados
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = activeId == null,
            onClick = { vm.select(null) },
            label = { Text("Por defecto") },
        )
        skins.forEach { skin ->
            FilterChip(
                selected = skin.id == activeId,
                onClick = { vm.select(skin.id) },
                label = { Text(skin.name) },
            )
        }
        FilterChip(
            selected = false,
            onClick = { creating = true },
            label = { Text("Nuevo") },
            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
        )
    }

    if (active != null) {
        SkinEditor(
            skin = active,
            vm = vm,
            onRename = { renaming = true },
            onDelete = { deleting = true },
        )
    }

    message?.let {
        Text(
            it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }

    if (creating) {
        NameDialog("Nuevo diseño", "", onDismiss = { creating = false }) {
            vm.create(it); creating = false
        }
    }
    if (renaming && active != null) {
        NameDialog("Renombrar diseño", active.name, onDismiss = { renaming = false }) {
            vm.rename(active.id, it); renaming = false
        }
    }
    if (deleting && active != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Eliminar \"${active.name}\"") },
            text = { Text("Se borra el diseño y sus imágenes. La barra vuelve a la normal.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(active.id); deleting = false }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancelar") } },
        )
    }
}

/** Barra de muestra que avanza sola, con el diseño activo o el normal. */
@Composable
private fun Preview(loaded: LoadedSkin?) {
    val transition = rememberInfiniteTransition(label = "preview")
    val fraction by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6_000, easing = LinearEasing), RepeatMode.Restart),
        label = "fraction",
    )
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (loaded != null) {
            SkinnedSeekBar(
                fraction = fraction, skin = loaded, isPlaying = true,
                onScrub = {}, onScrubEnd = {}, enabled = false,
            )
        } else {
            Slider(value = fraction, onValueChange = {}, enabled = true)
        }
    }
}

@Composable
private fun SkinEditor(
    skin: ProgressSkin,
    vm: ProgressSkinViewModel,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(skin.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onRename) { Icon(Icons.Default.Edit, contentDescription = "Renombrar") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Eliminar") }
        }

        val slots = if (skin.mode == TrackMode.REVEAL) {
            listOf(SkinSlot.THUMB, SkinSlot.PLAYED)
        } else SkinSlot.entries
        slots.forEach { slot -> SlotRow(skin, slot, vm) }

        Text("Modo de las barras", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrackMode.entries.forEach { mode ->
                FilterChip(
                    selected = skin.mode == mode,
                    onClick = { vm.setMode(skin.id, mode) },
                    label = { Text(mode.label) },
                )
            }
        }
        Hint(skin.mode.description)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Girar el indicador al reproducir", modifier = Modifier.weight(1f))
            Switch(checked = skin.spinThumb, onCheckedChange = { vm.setSpin(skin.id, it) })
        }

        Hint(
            "Indicador: PNG o WebP cuadrado, de 64 a 512 px. Barras: PNG o WebP horizontal, " +
                    "de 16 a 64 px de alto. Máximo 1 MB por imagen. Lo que no tenga imagen usa los colores del tema."
        )
    }
}

@Composable
private fun SlotRow(skin: ProgressSkin, slot: SkinSlot, vm: ProgressSkinViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setImage(skin.id, slot, uri)
    }
    val path = skin.path(slot)
    val label = if (slot == SkinSlot.PLAYED && skin.mode == TrackMode.REVEAL) "Barra completa" else slot.label

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = if (slot == SkinSlot.THUMB) 40.dp else 72.dp, height = 40.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (path != null) AsyncImage(model = File(path), contentDescription = null)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label)
            Text(
                if (path != null) "Imagen propia" else "Por defecto",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }) { Text("Elegir") }
        if (path != null) TextButton(onClick = { vm.clearImage(skin.id, slot) }) { Text("Quitar") }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text("Nombre") },
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name) }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}