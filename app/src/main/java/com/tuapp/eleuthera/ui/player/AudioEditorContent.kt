package com.tuapp.eleuthera.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tuapp.eleuthera.data.library.AudioGainSegment
import com.tuapp.eleuthera.data.settings.LoadedSkin
import com.tuapp.eleuthera.domain.model.Track
import com.tuapp.eleuthera.ui.common.TrackArt

/** Stateless editor UI: editing and playback actions are supplied by the screen wrapper. */
@Composable
fun AudioEditorContent(
    track: Track,
    state: AudioEditorState,
    skin: LoadedSkin?,
    playheadMs: Long,
    previewing: Boolean,
    hasAudioAfterEdit: Boolean,
    onClose: () -> Unit,
    onTabChange: (AudioEditorTab) -> Unit,
    onModeChange: (TrimMode) -> Unit,
    onRangeChange: (Long, Long) -> Unit,
    onPlayheadChange: (Long) -> Unit,
    onPlayheadDragStart: () -> Unit,
    onStepStart: (Long) -> Unit,
    onStepEnd: (Long) -> Unit,
    onSegmentRangeChange: (Long, Long, Long) -> Unit,
    onSegmentGainChange: (Long, Float) -> Unit,
    onAddSegment: () -> Unit,
    onRemoveSegment: (Long) -> Unit,
    onLyricsOffsetChange: (Long) -> Unit,
    onLyricsLineStartChange: (Int, Long) -> Unit,
    onTogglePreview: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmReplace by remember { mutableStateOf(false) }
    var editingCueId by remember { mutableStateOf<Int?>(null) }
    var timestampInput by remember { mutableStateOf("") }

    val (previewText, previewError) = remember(
        state.lyricsText,
        state.lyricsOffsetMs,
        state.durationMs,
        state.lyricsError,
    ) {
        val lyrics = state.lyricsText
        if (lyrics.isNullOrBlank()) {
            null to state.lyricsError
        } else {
            try {
                if (state.lyricsOffsetMs != 0L) {
                    try {
                        LyricsTimestampEditor.shift(lyrics, state.lyricsOffsetMs, state.durationMs) to null
                    } catch (_: Exception) {
                        lyrics to null
                    }
                } else {
                    lyrics to null
                }
            } catch (e: IllegalArgumentException) {
                null to e.message
            }
        }
    }
    val previewLines = remember(previewText) {
        previewText?.let(LyricsTimestampEditor::previewLines).orEmpty()
    }

    BackHandler(enabled = !state.saving, onBack = onClose)
    Surface(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose, enabled = !state.saving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                    Text("Editor de audio", style = MaterialTheme.typography.titleLarge)
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AudioEditorTab.entries.forEach { tab ->
                        FilterChip(
                            selected = state.tab == tab,
                            onClick = { onTabChange(tab) },
                            label = {
                                Text(
                                    when (tab) {
                                        AudioEditorTab.TRIM -> "Recortar"
                                        AudioEditorTab.LYRICS -> "Sincronizar letras"
                                        AudioEditorTab.VOLUME -> "Volumen por segmentos"
                                    },
                                )
                            },
                            enabled = !state.loading && !state.saving && !state.saved,
                        )
                    }
                }
            }

            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(12.dp))
                    TrackArt(track.uri, Modifier.size(220.dp))
                    Spacer(Modifier.height(20.dp))
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(20.dp))
                }
            }

            if (state.loading) {
                item { CircularProgressIndicator() }
            } else if (state.durationMs > 0) {
                when (state.tab) {
                    AudioEditorTab.TRIM -> item {
                        TrimPanel(
                            state = state,
                            skin = skin,
                            playheadMs = playheadMs,
                            previewing = previewing,
                            hasAudioAfterEdit = hasAudioAfterEdit,
                            onModeChange = onModeChange,
                            onRangeChange = onRangeChange,
                            onPlayheadChange = onPlayheadChange,
                            onPlayheadDragStart = onPlayheadDragStart,
                            onStepStart = onStepStart,
                            onStepEnd = onStepEnd,
                            onTogglePreview = onTogglePreview,
                            onSave = { confirmReplace = true },
                        )
                    }
                    AudioEditorTab.VOLUME -> item {
                        VolumeEditorPanel(
                            durationMs = state.durationMs,
                            segments = state.gainSegments,
                            skin = skin,
                            saving = state.saving,
                            saved = state.saved,
                            onRangeChange = onSegmentRangeChange,
                            onGainChange = onSegmentGainChange,
                            onAddSegment = onAddSegment,
                            onRemoveSegment = onRemoveSegment,
                            onSave = { confirmReplace = true },
                        )
                    }
                    AudioEditorTab.LYRICS -> {
                        lyricsEditorItems(
                            state = state,
                            skin = skin,
                            playheadMs = playheadMs,
                            previewing = previewing,
                            previewText = previewText,
                            previewError = previewError,
                            previewLines = previewLines,
                            onOffsetChange = onLyricsOffsetChange,
                            onLineStartChange = onLyricsLineStartChange,
                            onPlayheadChange = onPlayheadChange,
                            onPlayheadDragStart = onPlayheadDragStart,
                            onTogglePreview = onTogglePreview,
                            onSave = { confirmReplace = true },
                            onEditLine = { cueId, input ->
                                editingCueId = cueId
                                timestampInput = input
                            },
                        )
                    }
                }
            }

            state.error?.let { err ->
                item {
                    Text(
                        err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = {
                Text(
                    when (state.tab) {
                        AudioEditorTab.TRIM -> "¿Sustituir el archivo original?"
                        AudioEditorTab.VOLUME -> "¿Aplicar el cambio de volumen?"
                        AudioEditorTab.LYRICS -> "¿Guardar el ajuste de sincronización?"
                    },
                )
            },
            text = {
                Text(
                    when (state.tab) {
                        AudioEditorTab.TRIM -> if (state.mode == TrimMode.REMOVE_SELECTED) {
                            "Se quitará el tramo de ${formatTime(state.startMs)} a ${formatTime(state.endMs)} y se unirán las partes restantes. El audio se recodificará y sustituirá el original. No se podrá deshacer."
                        } else {
                            "Se conservará el tramo seleccionado, se recodificará el audio y se sustituirá el original. No se podrá deshacer."
                        }
                        AudioEditorTab.VOLUME ->
                            "Se aplicará el aumento de volumen por segmentos, se recodificará el audio y se sustituirá el original. No se podrá deshacer."
                        AudioEditorTab.LYRICS ->
                            "Se guardará la sincronización ajustada en el archivo de letras y se reemplazará la versión anterior."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReplace = false
                        onSave()
                    },
                ) {
                    Text(if (state.tab == AudioEditorTab.LYRICS) "Guardar" else "Sustituir")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReplace = false }) { Text("Cancelar") }
            },
        )
    }

    if (state.saved) {
        AlertDialog(
            onDismissRequest = onClose,
            title = {
                Text(
                    when (state.savedTab) {
                        AudioEditorTab.LYRICS -> "Sincronización guardada"
                        AudioEditorTab.VOLUME -> "Volumen ajustado"
                        else -> if (state.mode == TrimMode.REMOVE_SELECTED) "Tramo eliminado" else "Archivo recortado"
                    },
                )
            },
            text = {
                Text(
                    state.saveMessage ?: when (state.savedTab) {
                        AudioEditorTab.LYRICS -> "Se ha actualizado la sincronización de las letras."
                        AudioEditorTab.VOLUME -> "El volumen se ha procesado y el audio actualizado en su ubicación."
                        else -> if (state.mode == TrimMode.REMOVE_SELECTED) {
                            "El tramo se ha quitado y las partes restantes se han unido en el archivo original."
                        } else {
                            "El audio se ha convertido a M4A y actualizado en su ubicación."
                        }
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = onClose) { Text("Aceptar") }
            },
        )
    }

    val editingLine = previewLines.firstOrNull { it.cueId == editingCueId }
    if (editingLine != null) {
        val newStartMs = parseLyricsTimestamp(timestampInput)
        AlertDialog(
            onDismissRequest = { editingCueId = null },
            title = { Text("Cambiar inicio de línea") },
            text = {
                Column {
                    Text(
                        editingLine.text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    OutlinedTextField(
                        value = timestampInput,
                        onValueChange = { timestampInput = it },
                        label = { Text("Inicio (mm:ss.mmm o segundos)") },
                        singleLine = true,
                        isError = timestampInput.isNotBlank() && newStartMs == null,
                    )
                    TextButton(
                        onClick = { timestampInput = formatLyricsTimestamp(playheadMs) },
                        enabled = !state.saving,
                    ) {
                        Text("Usar posición actual (${formatLyricsTimestamp(playheadMs)})")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        newStartMs?.let { onLyricsLineStartChange(editingLine.cueId, it) }
                        editingCueId = null
                    },
                    enabled = newStartMs != null && !state.saving,
                ) {
                    Text("Aplicar")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCueId = null }) { Text("Cancelar") }
            },
        )
    }
}

private fun LazyListScope.lyricsEditorItems(
    state: AudioEditorState,
    skin: LoadedSkin?,
    playheadMs: Long,
    previewing: Boolean,
    previewText: String?,
    previewError: String?,
    previewLines: List<LyricsPreviewLine>,
    onOffsetChange: (Long) -> Unit,
    onLineStartChange: (Int, Long) -> Unit,
    onPlayheadChange: (Long) -> Unit,
    onPlayheadDragStart: () -> Unit,
    onTogglePreview: () -> Unit,
    onSave: () -> Unit,
    onEditLine: (Int, String) -> Unit,
) {
    val activeLineIndex = previewLines.indexOfLast { line ->
        line.startMs?.let { it <= playheadMs } == true
    }

    item {
        Column(Modifier.fillMaxWidth()) {
            Text(
                "Desfase global",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "${formatOffset(state.lyricsOffsetMs)} · el tiempo de cada línea cambia en tiempo real",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            Slider(
                value = state.lyricsOffsetMs.toFloat(),
                onValueChange = { onOffsetChange(it.toLong()) },
                valueRange = -MAX_LYRICS_OFFSET_MS.toFloat()..MAX_LYRICS_OFFSET_MS.toFloat(),
                steps = 119,
                enabled = !state.saving && state.lyricsText != null,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    onClick = { onOffsetChange(state.lyricsOffsetMs - LYRICS_OFFSET_STEP_MS) },
                    enabled = !state.saving && state.lyricsText != null,
                ) { Text("−100 ms") }
                TextButton(
                    onClick = { onOffsetChange(0) },
                    enabled = !state.saving && state.lyricsOffsetMs != 0L,
                ) { Text("Reiniciar") }
                TextButton(
                    onClick = { onOffsetChange(state.lyricsOffsetMs + LYRICS_OFFSET_STEP_MS) },
                    enabled = !state.saving && state.lyricsText != null,
                ) { Text("+100 ms") }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Líneas de la letra · toca un tiempo para editar o usa 'Marcar' para sincronizar",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
        }
    }

    stickyHeader {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 2.dp,
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                SkinnedRangeSeekBar(
                    startMs = 0,
                    endMs = state.durationMs,
                    durationMs = state.durationMs,
                    skin = skin,
                    playheadMs = playheadMs,
                    onRangeChange = { _, _ -> },
                    onPlayheadChange = onPlayheadChange,
                    onPlayheadDragStart = onPlayheadDragStart,
                    enabled = !state.saving,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Posición: ${formatTime(playheadMs)}", style = MaterialTheme.typography.labelMedium)
                    FilledIconButton(onClick = onTogglePreview, enabled = !state.saving) {
                        Icon(
                            if (previewing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (previewing) "Pausar canción" else "Escuchar canción",
                        )
                    }
                    Text("Duración: ${formatTime(state.durationMs)}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    if (previewError != null) {
        item {
            Text(
                previewError,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    } else if (previewText != null) {
        itemsIndexed(previewLines, key = { index, line -> "${line.cueId}_$index" }) { index, line ->
            val isActiveLine = index == activeLineIndex
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                shape = RoundedCornerShape(8.dp),
                color = if (isActiveLine) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            val input = line.startMs?.let(::formatLyricsTimestamp) ?: formatLyricsTimestamp(playheadMs)
                            onEditLine(line.cueId, input)
                        },
                        enabled = !state.saving && !state.saved && line.cueId >= 0,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    ) {
                        Text(
                            text = line.startMs?.let { formatLyricsTimestamp(it) } ?: "--:--.---",
                            color = if (line.startMs != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    IconButton(
                        onClick = {
                            if (line.cueId >= 0) {
                                onLineStartChange(line.cueId, playheadMs)
                            }
                        },
                        enabled = !state.saving && !state.saved && line.cueId >= 0,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = "Marcar en la posición actual (${formatLyricsTimestamp(playheadMs)})",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = line.text,
                        color = if (isActiveLine) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isActiveLine) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                    )
                }
            }
        }
    }

    item {
        Column(Modifier.fillMaxWidth()) {
            Text(
                "El ajuste se aplica a las marcas LRC/SRT. La letra se guarda junto a la canción; el audio no se modifica.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onSave,
                enabled = !state.saving && !state.saved &&
                    state.lyricsText != null && (state.lyricsOffsetMs != 0L || state.lyricsLineEdited) &&
                    previewText != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.saving) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("  Guardando letras…")
                } else {
                    Text("Guardar sincronización")
                }
            }
        }
    }
}

@Composable
private fun TrimPanel(
    state: AudioEditorState,
    skin: LoadedSkin?,
    playheadMs: Long,
    previewing: Boolean,
    hasAudioAfterEdit: Boolean,
    onModeChange: (TrimMode) -> Unit,
    onRangeChange: (Long, Long) -> Unit,
    onPlayheadChange: (Long) -> Unit,
    onPlayheadDragStart: () -> Unit,
    onStepStart: (Long) -> Unit,
    onStepEnd: (Long) -> Unit,
    onTogglePreview: () -> Unit,
    onSave: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.mode == TrimMode.KEEP_SELECTED,
            onClick = { onModeChange(TrimMode.KEEP_SELECTED) },
            label = { Text("Recortar límites") },
            enabled = !state.saving,
        )
        FilterChip(
            selected = state.mode == TrimMode.REMOVE_SELECTED,
            onClick = { onModeChange(TrimMode.REMOVE_SELECTED) },
            label = { Text("Quitar tramo") },
            enabled = !state.saving,
        )
    }
    Spacer(Modifier.height(12.dp))
    SkinnedRangeSeekBar(
        startMs = state.startMs,
        endMs = state.endMs,
        durationMs = state.durationMs,
        skin = skin,
        playheadMs = playheadMs,
        onRangeChange = onRangeChange,
        onPlayheadChange = onPlayheadChange,
        onPlayheadDragStart = onPlayheadDragStart,
        enabled = !state.saving && !state.saved,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            "${if (state.mode == TrimMode.REMOVE_SELECTED) "Quitar desde" else "Inicio"}: ${formatTime(state.startMs)}",
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            "${if (state.mode == TrimMode.REMOVE_SELECTED) "hasta" else "Fin"}: ${formatTime(state.endMs)}",
            style = MaterialTheme.typography.labelMedium,
        )
    }
    Text(
        "Posición: ${formatTime(playheadMs)}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimeControl(
            label = if (state.mode == TrimMode.REMOVE_SELECTED) "Desde" else "Inicio",
            time = state.startMs,
            onStep = onStepStart,
        )
        FilledIconButton(onClick = onTogglePreview, enabled = !state.saving) {
            Icon(
                if (previewing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (previewing) "Pausar previsualización" else "Escuchar resultado",
            )
        }
        TimeControl(
            label = if (state.mode == TrimMode.REMOVE_SELECTED) "Hasta" else "Fin",
            time = state.endMs,
            onStep = onStepEnd,
        )
    }
    Spacer(Modifier.height(20.dp))
    Text(
        if (state.mode == TrimMode.REMOVE_SELECTED) {
            "Se quitará el tramo seleccionado y se unirán las partes anterior y posterior. El audio se sustituirá por un M4A/AAC."
        } else {
            "El tramo seleccionado se convertirá a M4A/AAC y sustituirá el original en la misma ubicación."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = onSave,
        enabled = !state.saving && !state.saved && hasAudioAfterEdit,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.saving) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("  Guardando…")
        } else {
            Text(if (state.mode == TrimMode.REMOVE_SELECTED) "Quitar tramo" else "Guardar recorte")
        }
    }
}

@Composable
private fun VolumeEditorPanel(
    durationMs: Long,
    segments: List<AudioGainSegment>,
    skin: LoadedSkin?,
    saving: Boolean,
    saved: Boolean,
    onRangeChange: (Long, Long, Long) -> Unit,
    onGainChange: (Long, Float) -> Unit,
    onAddSegment: () -> Unit,
    onRemoveSegment: (Long) -> Unit,
    onSave: () -> Unit,
) {
    Text(
        "Añade intervalos y ajusta cuánto aumentar su volumen. Si se solapan, los aumentos se suman; valores altos pueden saturar el audio.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    segments.forEachIndexed { index, segment ->
        Surface(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Segmento ${index + 1}", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { onRemoveSegment(segment.id) }, enabled = !saving && !saved) {
                        Text("Quitar")
                    }
                }
                SkinnedRangeSeekBar(
                    startMs = segment.startMs,
                    endMs = segment.endMs,
                    durationMs = durationMs,
                    skin = skin,
                    onRangeChange = { start, end -> onRangeChange(segment.id, start, end) },
                    enabled = !saving && !saved,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Inicio: ${formatTime(segment.startMs)}", style = MaterialTheme.typography.labelSmall)
                    Text("Fin: ${formatTime(segment.endMs)}", style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    "Aumento: ${"%.1f".format(segment.gainDb)} dB",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = segment.gainDb,
                    onValueChange = { onGainChange(segment.id, it) },
                    valueRange = 0f..12f,
                    steps = 23,
                    enabled = !saving && !saved,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("0 dB · original", style = MaterialTheme.typography.labelSmall)
                    Text("+12 dB", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    OutlinedButton(
        onClick = onAddSegment,
        enabled = !saving && !saved && segments.size < MAX_GAIN_SEGMENTS,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Añadir segmento")
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "Al guardar se recodificará la canción en M4A/AAC y se sustituirá el archivo actual. La previsualización no aplica estos cambios.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = onSave,
        enabled = !saving && !saved && segments.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (saving) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("  Procesando audio…")
        } else {
            Text("Aplicar volumen")
        }
    }
}

@Composable
private fun TimeControl(label: String, time: Long, onStep: (Long) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(formatTime(time), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = { onStep(-1_000) }) { Text("−1 s") }
            OutlinedButton(onClick = { onStep(1_000) }) { Text("+1 s") }
        }
    }
}

private fun formatTime(ms: Long): String {
    val seconds = (ms / 1_000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

private fun formatLyricsTimestamp(ms: Long): String {
    val time = ms.coerceAtLeast(0)
    return "%d:%02d.%03d".format(time / 60_000, time / 1_000 % 60, time % 1_000)
}

private fun parseLyricsTimestamp(value: String): Long? {
    val trimmed = value.trim()
    val mmssMatch = Regex("""(\d+):([0-5]\d)(?:[.,](\d{1,3}))?""").matchEntire(trimmed)
    if (mmssMatch != null) {
        val minutes = mmssMatch.groupValues[1].toLongOrNull() ?: return null
        val seconds = mmssMatch.groupValues[2].toLongOrNull() ?: return null
        val millis = mmssMatch.groupValues[3].padEnd(3, '0').toLongOrNull() ?: 0
        if (minutes > (Long.MAX_VALUE / 1_000 - seconds) / 60) return null
        return (minutes * 60 + seconds) * 1_000 + millis
    }
    val secMatch = Regex("""(\d+)(?:[.,](\d{1,3}))?""").matchEntire(trimmed)
    if (secMatch != null) {
        val seconds = secMatch.groupValues[1].toLongOrNull() ?: return null
        val millis = secMatch.groupValues[2].padEnd(3, '0').toLongOrNull() ?: 0
        return seconds * 1_000 + millis
    }
    return null
}

private fun formatOffset(ms: Long): String =
    (if (ms > 0) "+" else "") + "%.1f s".format(ms / 1_000.0)

private const val LYRICS_OFFSET_STEP_MS = 100L
private const val MAX_LYRICS_OFFSET_MS = 30_000L
private const val MAX_GAIN_SEGMENTS = 20
