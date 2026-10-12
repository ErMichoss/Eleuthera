package com.tuapp.eleuthera.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuapp.eleuthera.domain.model.AppFont
import com.tuapp.eleuthera.domain.model.AudioFormat
import com.tuapp.eleuthera.domain.model.ComponentStyle
import com.tuapp.eleuthera.domain.model.ThemeConfig
import com.tuapp.eleuthera.ui.login.CookieLoginActivity
import com.tuapp.eleuthera.ui.theme.AppButton
import com.tuapp.eleuthera.ui.theme.AppCard

private fun argb(hex: Long) = hex.toInt()

/** Combinaciones completas listas para usar. */
private val presets = listOf(
    "Eleuthera" to ThemeConfig(),
    "Claro" to ThemeConfig(
        primary = argb(0xFF2E6BE6), secondary = argb(0xFF5B7DB8),
        background = argb(0xFFF6F7F9), accent = argb(0xFFE6612E),
    ),
    "AMOLED" to ThemeConfig(
        primary = argb(0xFFBB86FC), secondary = argb(0xFF7E6BA8),
        background = argb(0xFF000000), accent = argb(0xFF03DAC6),
    ),
    "Océano" to ThemeConfig(
        primary = argb(0xFF4FC3F7), secondary = argb(0xFF4A90A4),
        background = argb(0xFF0D1B2A), accent = argb(0xFFFFD166),
    ),
    "Atardecer" to ThemeConfig(
        primary = argb(0xFFFF7043), secondary = argb(0xFFB5654A),
        background = argb(0xFF1F1410), accent = argb(0xFFFFCA28),
    ),
    "Papel" to ThemeConfig(
        primary = argb(0xFF8D5A2B), secondary = argb(0xFFA1887F),
        background = argb(0xFFF4ECD8), accent = argb(0xFF2E7D32),
    ),
)

private val accentSwatches = listOf(
    0xFF1DB954, 0xFF2E6BE6, 0xFF4FC3F7, 0xFF03DAC6, 0xFFBB86FC, 0xFFE91E63,
    0xFFF44336, 0xFFFF7043, 0xFFFFB300, 0xFFFFD166, 0xFF8D5A2B, 0xFF9E9E9E,
).map(::argb)

private val backgroundSwatches = listOf(
    0xFF000000, 0xFF121212, 0xFF1C1B1F, 0xFF0D1B2A, 0xFF1F1410, 0xFF14201A,
    0xFF2B2B2B, 0xFFE0E0E0, 0xFFF4ECD8, 0xFFF6F7F9, 0xFFFFFFFF,
).map(::argb)

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    vm: ThemeViewModel,
    cookiesVm: CookiesViewModel = hiltViewModel(),
    downloadsVm: DownloadSettingsViewModel = hiltViewModel(),
    libraryVm: LibrarySettingsViewModel = hiltViewModel(),
) {
    val config by vm.config.collectAsStateWithLifecycle()

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Ajustes",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = vm::reset) { Text("Restablecer") }
        }

        Preview()

        SectionTitle("Temas rápidos")
        ScrollRow {
            presets.forEach { (name, preset) ->
                val selected = config.primary == preset.primary &&
                    config.background == preset.background && config.accent == preset.accent
                FilterChip(
                    selected = selected,
                    // Un tema rápido cambia los colores; conserva fuente, bordes y estilo.
                    onClick = {
                        vm.update {
                            it.copy(
                                primary = preset.primary, secondary = preset.secondary,
                                background = preset.background, accent = preset.accent,
                            )
                        }
                    },
                    label = { Text(name) },
                    leadingIcon = {
                        Box(
                            Modifier.size(14.dp).clip(CircleShape)
                                .background(Color(preset.primary))
                        )
                    },
                )
            }
        }

        ColorRow("Color primario", config.primary, accentSwatches) { c ->
            vm.update { it.copy(primary = c) }
        }
        ColorRow("Color secundario", config.secondary, accentSwatches) { c ->
            vm.update { it.copy(secondary = c) }
        }
        ColorRow("Acento", config.accent, accentSwatches) { c ->
            vm.update { it.copy(accent = c) }
        }
        ColorRow("Fondo", config.background, backgroundSwatches) { c ->
            vm.update { it.copy(background = c) }
        }

        SectionTitle("Tipografía")
        ScrollRow {
            AppFont.entries.forEach { font ->
                FilterChip(
                    selected = config.font == font,
                    onClick = { vm.update { it.copy(font = font) } },
                    label = { Text(font.label) },
                )
            }
        }

        SectionTitle("Bordes redondeados: ${config.cornerRadiusDp} dp")
        Slider(
            value = config.cornerRadiusDp.toFloat(),
            onValueChange = { v -> vm.update { it.copy(cornerRadiusDp = v.toInt()) } },
            valueRange = 0f..28f,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        SectionTitle("Estilo de botones y tarjetas")
        ScrollRow {
            ComponentStyle.entries.forEach { style ->
                FilterChip(
                    selected = config.style == style,
                    onClick = { vm.update { it.copy(style = style) } },
                    label = { Text(style.label) },
                )
            }
        }

        LibraryFoldersSection(libraryVm)
        DownloadSection(downloadsVm)
        CookiesSection(cookiesVm)
    }
}

/** Carpetas que muestra la biblioteca. */
@Composable
private fun LibraryFoldersSection(vm: LibrarySettingsViewModel) {
    val folders by vm.folders.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.add(uri)
    }

    SectionTitle("Carpetas de la biblioteca")
    Column(Modifier.padding(horizontal = 16.dp)) {
        FolderRow("Music/Eleuthera (descargas)", onRemove = null)
        folders.forEach { path ->
            FolderRow(path.trimEnd('/').ifEmpty { "Almacenamiento completo" }) { vm.remove(path) }
        }
        TextButton(onClick = { picker.launch(null) }) {
            Icon(Icons.Default.CreateNewFolder, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Añadir carpeta")
        }
        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun FolderRow(label: String, onRemove: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Quitar carpeta")
            }
        } else {
            Spacer(Modifier.height(48.dp))
        }
    }
}

/** Formato en el que se guardan las descargas nuevas. */
@Composable
private fun DownloadSection(vm: DownloadSettingsViewModel) {
    val format by vm.format.collectAsStateWithLifecycle()
    SectionTitle("Formato de descarga")
    ScrollRow {
        AudioFormat.entries.forEach { option ->
            FilterChip(
                selected = format == option,
                onClick = { vm.setFormat(option) },
                label = { Text(option.label) },
            )
        }
    }
    Text(
        when (format) {
            AudioFormat.MP3 -> "Se convierte a MP3: funciona en cualquier aparato, pero tarda más."
            AudioFormat.M4A -> "Se guarda el audio original de YouTube: más rápido y sin pérdida añadida."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

/** Selector libre: tono, saturación y brillo, o código hexadecimal. */
@Composable
private fun ColorPickerDialog(initial: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var saturation by remember { mutableFloatStateOf(start[1]) }
    var brightness by remember { mutableFloatStateOf(start[2]) }
    var hex by remember { mutableStateOf("%06X".format(initial and 0xFFFFFF)) }

    val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
    fun syncHex() {
        val c = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
        hex = "%06X".format(c and 0xFFFFFF)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Color personalizado") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(56.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(Color(argb))
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                )
                Text("Tono", style = MaterialTheme.typography.labelMedium)
                Slider(hue, { hue = it; syncHex() }, valueRange = 0f..360f)
                Text("Saturación", style = MaterialTheme.typography.labelMedium)
                Slider(saturation, { saturation = it; syncHex() })
                Text("Brillo", style = MaterialTheme.typography.labelMedium)
                Slider(brightness, { brightness = it; syncHex() })
                OutlinedTextField(
                    value = hex,
                    onValueChange = { input ->
                        hex = input.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                        if (hex.length == 6) {
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(0xFF000000.toInt() or hex.toInt(16), hsv)
                            hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]
                        }
                    },
                    singleLine = true,
                    label = { Text("Hexadecimal") },
                    prefix = { Text("#") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(argb); onDismiss() }) { Text("Usar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Cargar o quitar el cookies.txt de YouTube. */
@Composable
private fun CookiesSection(vm: CookiesViewModel) {
    val present by vm.present.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(uri)
    }
    val context = LocalContext.current

    SectionTitle("Cuenta de YouTube (cookies)")
    Column(
        Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (present) "Cookies cargadas: se usan en búsquedas y descargas."
            else "Sin cookies: las peticiones van como usuario anónimo.",
            style = MaterialTheme.typography.bodyMedium,
        )
        AppButton(
            onClick = { context.startActivity(Intent(context, CookieLoginActivity::class.java)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Iniciar sesión en YouTube")
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                Text("Importar archivo (.txt o .json)")
            }
            if (present) TextButton(onClick = vm::clear) { Text("Quitar") }
        }
        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Muestra de cómo quedan una tarjeta y un botón con la configuración actual. */
@Composable
private fun Preview() {
    AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Vista previa", style = MaterialTheme.typography.titleMedium)
            Text(
                "Así se ven las tarjetas, los textos y los botones.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppButton(onClick = {}) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Reproducir")
                }
                Box(
                    Modifier.size(20.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary)
                )
                Box(
                    Modifier.size(20.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
    )
}

@Composable
private fun ScrollRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun ColorRow(title: String, selected: Int, swatches: List<Int>, onPick: (Int) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    if (picking) ColorPickerDialog(selected, onPick, onDismiss = { picking = false })

    SectionTitle(title)
    Spacer(Modifier.height(4.dp))
    ScrollRow {
        // Primera muestra: color libre. Si el color actual no es de la paleta, se muestra aquí.
        val custom = selected !in swatches
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (custom) Color(selected) else Color.Transparent)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable { picking = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Palette,
                contentDescription = "Color personalizado",
                tint = when {
                    !custom -> MaterialTheme.colorScheme.onSurface
                    Color(selected).luminance() > 0.5f -> Color.Black
                    else -> Color.White
                },
            )
        }
        swatches.forEach { argb ->
            val color = Color(argb)
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { onPick(argb) },
                contentAlignment = Alignment.Center,
            ) {
                if (argb == selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Seleccionado",
                        tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                    )
                }
            }
        }
    }
}
