package com.tuapp.eleuthera.ui.musicbrainz

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tuapp.eleuthera.ui.platform.LocalRemoteImage
import com.tuapp.eleuthera.ui.platform.LocalToast
import com.tuapp.eleuthera.ui.settings.OptionChip
import com.tuapp.eleuthera.ui.theme.AppButton
import com.tuapp.eleuthera.ui.theme.AppCard

/**
 * Explorador de MusicBrainz, igual en Android y escritorio. Cada app pone el marco (barra de
 * título, botón de volver) y, si puede, el inicio de sesión con [onLogin].
 */
@Composable
fun MusicBrainzContent(
    viewModel: MusicBrainzController,
    modifier: Modifier = Modifier,
    /** null = esta plataforma no inicia sesión (se oculta el botón). */
    onLogin: (() -> Unit)? = null,
) {
    val toast = LocalToast.current
    LaunchedEffect(viewModel.notice) {
        viewModel.notice?.let {
            toast(it)
            viewModel.notice = null
        }
    }

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Panel de búsqueda usando el componente AppCard
            AppCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    if (onLogin != null && viewModel.canLogIn) {
                        AppButton(
                            onClick = onLogin,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !viewModel.loggedIn,
                        ) {
                            Text(if (viewModel.loggedIn) "Sesión iniciada en MusicBrainz" else "Iniciar Sesión en MusicBrainz")
                        }
                    }

                    // Selector de tipo (Recording o Album)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OptionChip(
                            selected = viewModel.searchType == SearchType.RECORDING,
                            onClick = { viewModel.searchType = SearchType.RECORDING },
                            label = "Canción (Recording)",
                        )
                        OptionChip(
                            selected = viewModel.searchType == SearchType.RELEASE,
                            onClick = { viewModel.searchType = SearchType.RELEASE },
                            label = "Álbum (Release)",
                        )
                    }

                    // Campos de Texto
                    OutlinedTextField(
                        value = viewModel.titleInput,
                        onValueChange = { viewModel.titleInput = it },
                        label = { Text(if (viewModel.searchType == SearchType.RECORDING) "Nombre de la Canción *" else "Nombre del Álbum *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = viewModel.artistInput,
                        onValueChange = { viewModel.artistInput = it },
                        label = { Text("Artista / Banda (Opcional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    AppButton(
                        onClick = { viewModel.search() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !viewModel.isLoading
                    ) {
                        if (viewModel.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Buscar Metadatos")
                    }
                }
            }

            // Mensaje de Error
            viewModel.errorMessage?.let { error ->
                Text(text = error, color = MaterialTheme.colorScheme.error)
            }

            // Resultados de búsqueda
            Box(modifier = Modifier.fillMaxSize()) {
                if (viewModel.searchType == SearchType.RECORDING) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(viewModel.recordingsResult) { rec ->
                            val release = rec.releases?.firstOrNull()
                            val releaseMbid = release?.id ?: ""
                            val artistName = rec.artistCredit?.joinToString(" ") { it.name ?: "" }
                                ?: "Desconocido"
                            val coverUrl =
                                if (releaseMbid.isNotBlank()) "https://coverartarchive.org/release/$releaseMbid/front-250" else null

                            RecordingRowItem(
                                title = rec.title ?: "N/A",
                                artist = artistName,
                                releaseTitle = release?.title ?: "N/A",
                                coverUrl = coverUrl,
                                onImageClick = { viewModel.openRecordingModal(rec, releaseMbid) }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(viewModel.releasesResult) { rel ->
                            val artistName = rel.artistCredit?.joinToString(" ") { it.name ?: "" }
                                ?: "Desconocido"
                            val coverUrl = rel.id?.let { "https://coverartarchive.org/release/$it/front-250" }

                            RecordingRowItem(
                                title = rel.title?: "Desconocido",
                                artist = artistName,
                                releaseTitle = rel.status ?: "Álbum",
                                coverUrl = coverUrl,
                                onImageClick = { viewModel.openReleaseModal(rel) }
                            )
                        }
                    }
                }
            }
        }

    // Modal para ver la carátula y detalles en alta resolución
    MetadataModal(viewModel)
}

// Modal de metadatos
@Composable
fun MetadataModal(viewModel: MusicBrainzController){
    if (viewModel.isModalOpen) {
        // En escritorio la ventana puede ser muy ancha: el diálogo no pasa de 560 dp.
        Dialog(
            onDismissRequest = { viewModel.closeImageModal() },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            AppCard(
                modifier = Modifier
                    .padding(24.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Título del Modal
                    Text(
                        text = viewModel.selectedImageTitle ?: "Detalles",
                        style = MaterialTheme.typography.titleLarge
                    )

                    // Imagen en alta resolución
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val fullUrl = viewModel.selectedImageMbid?.takeIf { it.isNotBlank() }
                            ?.let { "https://coverartarchive.org/release/$it/front-500" }
                        CoverImage(fullUrl, Modifier.fillMaxHeight().aspectRatio(1f))
                    }

                    HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

                    // Metadatos detallados scrolleables
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        viewModel.selectedRecording?.let { rec ->
                            val artistName = rec.artistCredit?.joinToString(", ") { it.name ?: "" }
                                .takeIf { !it.isNullOrBlank() } ?: "Desconocido"

                            // 1. Buscamos primero en genres y luego en tagList
                            val genresList = rec.genres ?: rec.tagList
                            val genres = genresList?.joinToString(", ") { it.name ?: "" }
                                .takeIf { !it.isNullOrBlank() } ?: "No especificado"

                            val release = rec.releases?.firstOrNull()
                            val year = release?.date?.take(4) ?: "N/A"

                            var trackNum: String? = null
                            var discNum: String? = null

                            release?.media?.forEachIndexed { index, media ->
                                val foundTrack = media.tracks?.find { track ->
                                    track.title.equals(rec.title, ignoreCase = true)
                                }
                                if (foundTrack != null) {
                                    trackNum = foundTrack.number
                                    discNum = media.position?.toString() ?: (index + 1).toString()
                                    return@forEachIndexed
                                }
                            }

                            item { MetadataRow("Artista:", artistName) }
                            item { MetadataRow("Género:", genres) } // Ahora mostrará "classic rock, hard rock, rock"
                            item { MetadataRow("Álbum:", release?.title ?: "N/A") }
                            item { MetadataRow("Año:", year) }
                            item { MetadataRow("Duración:", formatDuration(rec.length)) }

                            item { MetadataRow("Número de Pista:", trackNum ?: "N/A") }
                            discNum?.let { num ->
                                item { MetadataRow("Número de Disco:", num) }
                            }

                            item { MetadataRow("Recording MBID:", rec.id!!) }
                            release?.id?.let { item { MetadataRow("Release MBID:", it) } }
                        }

                        viewModel.selectedRelease?.let { rel ->
                            val artistName = rel.artistCredit?.joinToString(", ") { it.name ?: "" }.takeIf { !it.isNullOrBlank() } ?: "Desconocido"
                            val year = rel.date?.take(4) ?: "N/A"

                            // Metadatos principales del álbum arriba
                            item { MetadataRow("Artista / Banda:", artistName) }
                            item { MetadataRow("Estado:", rel.status ?: "Álbum") }
                            item { MetadataRow("Año de Lanzamiento:", year) }
                            item { MetadataRow("País:", rel.country ?: "N/A") }
                            item { MetadataRow("Total de Pistas:", rel.trackCount?.toString() ?: "N/A") }
                            item { MetadataRow("Release MBID:", rel.id ?: "ID desconocido") }

                            // Botón para cargar los detalles de las canciones bajo demanda
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                AppButton(
                                    onClick = { viewModel.loadAllTracksDetails() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Cargar detalles de las canciones")
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            // Separador y Título de la lista de canciones
                            item {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Canciones del Álbum",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                HorizontalDivider()
                            }

                            // Procesamiento seguro de las pistas del álbum
                            val allTracks = rel.media?.flatMap { media ->
                                val discNumber = media.position ?: 1
                                media.tracks.orEmpty().map { track ->
                                    Triple(discNumber, track.number?.toIntOrNull() ?: 0, track)
                                }
                            }?.sortedWith(compareBy({ it.first }, { it.second }))

                            if (!allTracks.isNullOrEmpty()) {
                                allTracks.forEachIndexed { index, (disc, _, track) ->
                                    val previousDisc = if (index > 0) allTracks[index - 1].first else -1

                                    // Si es el primer elemento o el disco cambia respecto al anterior, inyectamos la cabecera
                                    if (disc != previousDisc) {
                                        item(key = "disc_${disc}_$index") {
                                            Text(
                                                text = "Disco $disc",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                            )
                                        }
                                    }

                                    item(key = "track_${index}_${track.id ?: track.title}") {
                                        val trackKey = track.id ?: track.title ?: ""
                                        val syncStatus = viewModel.trackSyncStatuses[trackKey] ?: MusicBrainzController.TrackSyncStatus.PENDING
                                        val isLoading = viewModel.trackLoadingStates[trackKey] == true

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    track.recording?.let { recording ->
                                                        val mbidForCover = recording.releases?.firstOrNull()?.id ?: viewModel.selectedRelease?.id
                                                        viewModel.openRecordingModalDirectly(recording, mbidForCover, parentRelease = viewModel.selectedRelease)
                                                    }
                                                }
                                                .padding(vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${track.number ?: "-"}. ${track.title ?: "Sin título"}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )

                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Animacion de carga
                                                if (isLoading) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(16.dp),
                                                        strokeWidth = 2.dp
                                                    )
                                                } else {
                                                    // Icono según el estado, que al hacer clic vuelve a disparar la recarga individual
                                                    IconButton(
                                                        onClick = { viewModel.reloadSingleTrack(track) },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        when (syncStatus) {
                                                            MusicBrainzController.TrackSyncStatus.SYNCED -> {
                                                                Icon(
                                                                    imageVector = Icons.Default.CheckCircle,
                                                                    contentDescription = "Sincronizado (Click para recargar)",
                                                                    tint = Color(0xFF4CAF50),
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                            MusicBrainzController.TrackSyncStatus.ERROR -> {
                                                                Icon(
                                                                    imageVector = Icons.Default.Error,
                                                                    contentDescription = "Error (Click para reintentar)",
                                                                    tint = Color(0xFFE53935),
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                            MusicBrainzController.TrackSyncStatus.PENDING -> {
                                                                // Un icono sutil de sincronización/recarga cuando está pendiente
                                                                Icon(
                                                                    imageVector = Icons.Default.Refresh,
                                                                    contentDescription = "Cargar pista",
                                                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                track.length?.let { lengthMs ->
                                                    Text(
                                                        text = formatDuration(lengthMs),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Fila inferior con el botón de Volver (si aplica) y el botón de Cerrar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Si venimos de un álbum, mostramos el botón para volver a él
                        if (viewModel.previousRelease != null) {
                            OutlinedButton(
                                onClick = { viewModel.backToReleaseModal() }
                            ) {
                                Text("← Volver al Álbum")
                            }
                        }

                        // Botón principal de cierre del modal
                        AppButton(
                            onClick = { viewModel.closeImageModal() }
                        ) {
                            Text("Cerrar")
                        }
                    }
                }
            }
        }
    }
}

// Funciones auxiliares
@Composable
fun RecordingRowItem(
    title: String,
    artist: String,
    releaseTitle: String,
    coverUrl: String?,
    onImageClick: () -> Unit
) {
    // Toda la tarjeta abre los detalles (antes solo la carátula).
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onImageClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CoverImage(coverUrl, Modifier.size(56.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(text = artist, style = MaterialTheme.typography.bodyMedium)
                Text(text = releaseTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
fun MetadataRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

fun formatDuration(ms: Long?): String {
    if (ms == null) return "--:--"
    val totalSeconds = ms / 1000
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}

/**
 * Carátula de Cover Art Archive. Muchas publicaciones no tienen: debajo hay un icono de disco,
 * que queda a la vista si la imagen no llega a cargarse.
 */
@Composable
private fun CoverImage(url: String?, modifier: Modifier) {
    val remoteImage = LocalRemoteImage.current
    Box(
        modifier.clip(MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Album,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxSize(0.6f),
        )
        if (!url.isNullOrBlank()) remoteImage(url, Modifier.fillMaxSize())
    }
}
