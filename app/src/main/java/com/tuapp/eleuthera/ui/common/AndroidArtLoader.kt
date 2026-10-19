package com.tuapp.eleuthera.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cómo saca Android las carátulas (lo usa TrackArt, que es común): la miniatura de
 * MediaStore y, si no hay (canciones de carpetas añadidas), la incrustada en el archivo.
 * MainActivity lo pone en LocalArtLoader.
 */
fun androidArtLoader(context: Context): suspend (Uri) -> ImageBitmap? = { uri ->
    withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.loadThumbnail(uri, Size(240, 240), null) }
            .recoverCatching { embeddedArt(context, uri) ?: throw it }
            .getOrNull()?.asImageBitmap()
    }
}

/** Carátula incrustada en el archivo, reducida a unos [maxPx] píxeles. */
private fun embeddedArt(context: Context, uri: Uri, maxPx: Int = 240): Bitmap? {
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(context, uri)
        val bytes = r.embeddedPicture ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (e: Exception) {
        null
    } finally {
        r.release()
    }
}