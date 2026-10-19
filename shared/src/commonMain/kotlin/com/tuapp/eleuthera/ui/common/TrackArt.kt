package com.tuapp.eleuthera.ui.common

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import com.tuapp.eleuthera.platform.PlatformUri
import com.tuapp.eleuthera.ui.platform.LocalArtLoader
import com.tuapp.eleuthera.ui.platform.decodeImage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Carátulas ya decodificadas, para que las listas no parpadeen al hacer scroll. */
private val artCache = object : LinkedHashMap<PlatformUri, ImageBitmap>(128, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PlatformUri, ImageBitmap>) = size > 96
}

private fun cached(uri: PlatformUri): ImageBitmap? = synchronized(artCache) { artCache[uri] }

/**
 * Carátula incrustada en el archivo (la miniatura del vídeo), o un icono si no tiene.
 * Cómo se saca depende de la plataforma ([LocalArtLoader]).
 */
@Composable
fun TrackArt(
    uri: PlatformUri?,
    modifier: Modifier = Modifier,
    placeholder: ImageVector = Icons.Default.MusicNote,
) {
    val loader = LocalArtLoader.current
    val bitmap by produceState(initialValue = uri?.let(::cached), uri) {
        if (uri == null) {
            value = null
            return@produceState
        }
        value = cached(uri) ?: loader(uri)?.also { synchronized(artCache) { artCache[uri] = it } }
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
fun PlaylistCover(covers: List<PlatformUri>, modifier: Modifier = Modifier, customCover: String? = null) {
    if (customCover != null) {
        val image by produceState<ImageBitmap?>(null, customCover) {
            value = withContext(Dispatchers.IO) {
                runCatching { decodeImage(File(customCover).readBytes()) }.getOrNull()
            }
        }
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
            image?.let {
                Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            }
        }
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