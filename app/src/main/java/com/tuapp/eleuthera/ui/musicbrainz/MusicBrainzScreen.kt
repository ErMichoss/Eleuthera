package com.tuapp.eleuthera.ui.musicbrainz

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicBrainzScreen(viewModel: MusicBrainzViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MusicBrainz Explorer (Compose)") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Panel de búsqueda
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    Button(
                        onClick = { viewModel.startOAuthLogin(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Iniciar Sesión en MusicBrainz")
                    }

                    // Selector de tipo (Recording o Album)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = viewModel.searchType == SearchType.RECORDING,
                            onClick = { viewModel.searchType = SearchType.RECORDING },
                            label = { Text("Canción (Recording)") }
                        )
                        FilterChip(
                            selected = viewModel.searchType == SearchType.RELEASE,
                            onClick = { viewModel.searchType = SearchType.RELEASE },
                            label = { Text("Álbum (Release)") }
                        )
                    }

                    // Campos de Texto
                    OutlinedTextField(
                        value = viewModel.titleInput,
                        onValueChange = { viewModel.titleInput = it },
                        label = { Text(if (viewModel.searchType == SearchType.RECORDING) "Nombre de la Canción *" else "Nombre del Álbum *") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = viewModel.artistInput,
                        onValueChange = { viewModel.artistInput = it },
                        label = { Text("Artista / Banda (Opcional)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
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

            // Resultados de busqueda
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

                            val hasFailed = viewModel.failedImages[rec.id] == true

                            RecordingRowItem(
                                title = rec.title,
                                artist = artistName,
                                releaseTitle = release?.title ?: "N/A",
                                coverUrl = coverUrl,
                                hasFailed = hasFailed,
                                onImageError = { viewModel.markImageAsFailed(rec.id) },
                                onImageClick = {
                                    viewModel.openRecordingModal(context = context, rec, releaseMbid)
                                }
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
                            val coverUrl = "https://coverartarchive.org/release/${rel.id}/front-250"

                            val hasFailed = viewModel.failedImages[rel.id] == true

                            RecordingRowItem(
                                title = rel.title,
                                artist = artistName,
                                releaseTitle = rel.status ?: "Álbum",
                                coverUrl = coverUrl,
                                hasFailed = hasFailed,
                                onImageError = { viewModel.markImageAsFailed(rel.id) },
                                onImageClick = {
                                    viewModel.openReleaseModal(rel)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal para ver la caratula en alta resolución
    /* TODO: Mostrar todos los metadatos relevantes en este modal
     * Artista
     * Genero
     * Compositor
     * Anio
     * Letrista
     * Numero de pista (opcional)
     * Numero de disco (opcional)
     * Artista del album? (puede que no se pueda)
     */
    if (viewModel.isModalOpen) {
        Dialog(onDismissRequest = { viewModel.closeImageModal() }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                        val fullUrl =
                            viewModel.selectedImageMbid?.let { "https://coverartarchive.org/release/$it/front" }
                        AsyncImage(
                            model = fullUrl,
                            contentDescription = "Carátula",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
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
                            // 1. Artista
                            val artistName = rec.artistCredit?.joinToString(", ") { it.name ?: "" }.takeIf { !it.isNullOrBlank() } ?: "Desconocido"

                            // 2. Género (Usa tagList tal como lo definiste en tu modelo)
                            val genres = rec.tagList?.joinToString(", ") { it.name ?: "" }.takeIf { !it.isNullOrBlank() } ?: "-"

                            // 3 y 5. Compositor y Letrista (Usa relationList de tu modelo)
                            var composer = "-"
                            var lyricist = "-"
                            rec.relationList?.forEach { rel ->
                                if (rel.type == "composer" && rel.artist?.name != null) composer = rel.artist.name
                                if (rel.type == "lyricist" && rel.artist?.name != null) lyricist = rel.artist.name
                            }

                            val release = rec.releases?.firstOrNull()

                            // 4. Año
                            val year = release?.date?.take(4) ?: "N/A"

                            // 6 y 7. Número de Pista y Disco
                            var trackNum: String? = null
                            var discNum: String? = null

                            release?.media?.let { mediaList ->
                                mediaList.forEachIndexed { index, media ->
                                    discNum = media.position?.toString() ?: (index + 1).toString()

                                    // Búsqueda por título para evitar el error de IDs distintos
                                    val foundTrack = media.track?.find { track ->
                                        track.title.equals(rec.title, ignoreCase = true)
                                    }

                                    if (foundTrack != null) {
                                        trackNum = foundTrack.number
                                        return@forEachIndexed
                                    }
                                }
                            }

                            // Pintar filas en el modal
                            item { MetadataRow("Artista:", artistName) }
                            item { MetadataRow("Género:", genres) }
                            item { MetadataRow("Compositor:", composer) }
                            item { MetadataRow("Letrista:", lyricist) }
                            item { MetadataRow("Álbum:", release?.title ?: "N/A") }
                            item { MetadataRow("Año:", year) }
                            item { MetadataRow("Duración:", formatDuration(rec.length)) }

                            trackNum?.let { item { MetadataRow("Número de Pista:", it) } }
                            discNum?.let { item { MetadataRow("Número de Disco:", it) } }

                            item { MetadataRow("Recording MBID:", rec.id) }
                            release?.id?.let { item { MetadataRow("Release MBID:", it) } }
                        }

                        viewModel.selectedRelease?.let { rel ->
                            val artistName = rel.artistCredit?.joinToString(", ") { it.name ?: "" }.takeIf { !it.isNullOrBlank() } ?: "Desconocido"
                            val year = rel.date?.take(4) ?: "N/A"

                            item { MetadataRow("Artista / Banda:", artistName) }
                            item { MetadataRow("Estado:", rel.status ?: "Álbum") }
                            item { MetadataRow("Año de Lanzamiento:", year) }
                            item { MetadataRow("País:", rel.country ?: "N/A") }
                            item { MetadataRow("Total de Pistas:", rel.trackCount?.toString() ?: "N/A") }
                            item { MetadataRow("Release MBID:", rel.id) }
                        }
                    }

                    // Botón de cierre
                    Button(
                        onClick = { viewModel.closeImageModal() },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cerrar")
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
    hasFailed: Boolean,          // Recibe si falló o no
    onImageError: () -> Unit,
    onImageClick: () -> Unit
) {
    val backgroundColor = if (hasFailed) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
    } else {
        CardDefaults.cardColors().containerColor
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clickable { onImageClick() },
                contentAlignment = Alignment.Center
            ) {
                if (hasFailed || coverUrl.isNullOrBlank()) {
                    Icon(
                        imageVector = Icons.Default.BrokenImage,
                        contentDescription = "Sin imagen",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = "Cover",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onError = { onImageError() }
                    )
                }
            }

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
    val seconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(ms)
    return String.format("%d:%02d", minutes, seconds)
}