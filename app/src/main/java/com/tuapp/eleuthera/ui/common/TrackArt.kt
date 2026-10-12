package com.tuapp.eleuthera.ui.common

import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Carátulas ya decodificadas, para que las listas no parpadeen al hacer scroll. */
private val artCache = LruCache<Uri, ImageBitmap>(96)

/** Carátula incrustada en el MP3 (la miniatura del vídeo), o un icono si no tiene. */
@Composable
fun TrackArt(
    uri: Uri?,
    modifier: Modifier = Modifier,
    placeholder: ImageVector = Icons.Default.MusicNote,
) {
    val context = LocalContext.current
    val bitmap by produceState(initialValue = uri?.let(artCache::get), uri) {
        if (uri == null) { value = null; return@produceState }
        value = artCache.get(uri) ?: withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.loadThumbnail(uri, Size(240, 240), null).asImageBitmap()
            }.getOrNull()?.also { artCache.put(uri, it) }
        }
    }

    Box(
        modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val art = bitmap
        if (art != null) {
            Image(
                bitmap = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Icon(
                placeholder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Portada de playlist: la imagen personalizada si hay una; si no, mosaico 2×2 con cuatro
 * canciones, o una sola carátula si hay menos.
 */
@Composable
fun PlaylistCover(covers: List<Uri>, modifier: Modifier = Modifier, customCover: String? = null) {
    if (customCover != null) {
        AsyncImage(
            model = File(customCover),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        )
        return
    }
    if (covers.size < 4) {
        TrackArt(covers.firstOrNull(), modifier, Icons.AutoMirrored.Filled.PlaylistPlay)
        return
    }
    Column(modifier) {
        for (row in 0..1) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (col in 0..1) {
                    TrackArt(covers[row * 2 + col], Modifier.weight(1f).fillMaxSize())
                }
            }
        }
    }
}
