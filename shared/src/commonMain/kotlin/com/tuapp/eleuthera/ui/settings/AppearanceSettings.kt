package com.tuapp.eleuthera.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.tuapp.eleuthera.ui.platform.PlatformBackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tuapp.eleuthera.domain.model.AppFont
import com.tuapp.eleuthera.domain.model.AudioFormat
import com.tuapp.eleuthera.domain.model.ComponentStyle
import com.tuapp.eleuthera.domain.model.ThemeConfig
import com.tuapp.eleuthera.ui.theme.AppButton
import com.tuapp.eleuthera.ui.theme.AppCard

private fun argb(hex: Long) = hex.toInt()

/** Combinaciones completas listas para usar. */
internal val presets = listOf(
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

/** Ajustes > Apariencia: tema, colores, tipografía y formas. */
@Composable
fun AppearanceSettingsPage(vm: ThemeUi, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val config by vm.config.collectAsState()
    var customColors by rememberSaveable { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    SettingsPageScaffold("Apariencia", onBack, modifier, header = { Preview() }) {
        SectionTitle("Tema")
        ScrollRow {
            presets.forEach { (name, preset) ->
                val selected = config.primary == preset.primary &&
                        config.background == preset.background && config.accent == preset.accent
                OptionChip(
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
                    label = name,
                    leading = {
                        Box(
                            Modifier.size(14.dp).clip(CircleShape)
                                .background(Color(preset.primary))
                        )
                    },
                )
            }
        }

        // Colores sueltos, plegados por defecto para que la página no se haga larga.
        TextButton(
            onClick = { customColors = !customColors },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Text("Personalizar colores")
            Spacer(Modifier.width(4.dp))
            Icon(
                if (customColors) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
            )
        }
        if (customColors) {
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
        }

        SectionTitle("Texto")
        ScrollRow {
            AppFont.entries.forEach { font ->
                OptionChip(
                    selected = config.font == font,
                    onClick = { vm.update { it.copy(font = font) } },
                    label = font.label,
                )
            }
        }

        SectionTitle("Formas")
        Text(
            "Bordes redondeados: ${config.cornerRadiusDp} dp",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Slider(
            value = config.cornerRadiusDp.toFloat(),
            onValueChange = { v -> vm.update { it.copy(cornerRadiusDp = v.toInt()) } },
            valueRange = 0f..28f,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            "Estilo de botones y tarjetas",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        ScrollRow {
            ComponentStyle.entries.forEach { style ->
                OptionChip(
                    selected = config.style == style,
                    onClick = { vm.update { it.copy(style = style) } },
                    label = style.label,
                )
            }
        }

        TextButton(
            onClick = { confirmReset = true },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        ) { Text("Restablecer apariencia", color = MaterialTheme.colorScheme.error) }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Restablecer apariencia") },
            text = { Text("Vuelven los colores, la tipografía y las formas originales.") },
            confirmButton = {
                TextButton(onClick = { vm.reset(); confirmReset = false }) { Text("Restablecer") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancelar") } },
        )
    }
}

/** Nombre del tema rápido activo, o "Personalizado". */
fun presetName(config: ThemeConfig): String =
    presets.firstOrNull { (_, p) ->
        p.primary == config.primary && p.background == config.background && p.accent == config.accent
    }?.first ?: "Personalizado"

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

/** Selector libre: tono, saturación y brillo, o código hexadecimal. */
@Composable
private fun ColorPickerDialog(initial: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val start = remember { colorToHsv(initial) }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var saturation by remember { mutableFloatStateOf(start[1]) }
    var brightness by remember { mutableFloatStateOf(start[2]) }
    var hex by remember { mutableStateOf("%06X".format(initial and 0xFFFFFF)) }

    val argb = hsvToColor(hue, saturation, brightness)
    fun syncHex() {
        val c = hsvToColor(hue, saturation, brightness)
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
                            val hsv = colorToHsv(0xFF000000.toInt() or hex.toInt(16))
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