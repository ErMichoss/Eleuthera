package com.tuapp.eleuthera.ui.musicbrainz

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicBrainzScreen(viewModel: MusicBrainzViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
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
            // --- PANEL DE BÚSQUEDA ---
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Selector de Tipo
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
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
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

            // --- RESULTADOS EN LISTA ---
            Box(modifier = Modifier.fillMaxSize()) {
                if (viewModel.searchType == SearchType.RECORDING) {
                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(viewModel.recordingsResult) { rec ->
                            val release = rec.releases?.firstOrNull()
                            val releaseMbid = release?.id ?: ""
                            val artistName = rec.artistCredit?.joinToString(" ") { it.name ?: "" } ?: "Desconocido"
                            val coverUrl = if (releaseMbid.isNotBlank()) "https://coverartarchive.org/release/$releaseMbid/front-250" else null

                            val hasFailed = viewModel.failedImages[rec.id] == true

                            RecordingRowItem(
                                title = rec.title,
                                artist = artistName,
                                releaseTitle = release?.title ?: "N/A",
                                coverUrl = coverUrl,
                                hasFailed = hasFailed,
                                onImageError = { viewModel.markImageAsFailed(rec.id) },
                                onImageClick = {
                                    viewModel.openImageModal("${rec.title} - $artistName", releaseMbid)
                                }
                            )
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(viewModel.releasesResult) { rel ->
                            val artistName = rel.artistCredit?.joinToString(" ") { it.name ?: "" } ?: "Desconocido"
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
                                    viewModel.openImageModal("${rel.title} - $artistName", rel.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal para ver la carátula en alta resolución
    if (viewModel.isModalOpen) {
        Dialog(onDismissRequest = { viewModel.closeImageModal() }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val fullUrl = viewModel.selectedImageMbid?.let { "https://coverartarchive.org/release/$it/front" }
                    AsyncImage(
                        model = fullUrl,
                        contentDescription = "Carátula en alta resolución",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}

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