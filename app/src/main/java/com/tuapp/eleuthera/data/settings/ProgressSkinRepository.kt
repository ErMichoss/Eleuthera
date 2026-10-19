package com.tuapp.eleuthera.data.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Diseños de la barra de progreso en Android. Todo es [ProgressSkinStore] (:shared); aquí
 * solo se lee la imagen elegida con el ContentResolver y se reduce con Bitmap.
 * Las imágenes van a filesDir/progress_skins, como antes.
 */
@Singleton
class ProgressSkinRepository @Inject constructor(@ApplicationContext private val ctx: Context) :
    ProgressSkinStore(File(ctx.filesDir, "progress_skins")) {

    override fun inspect(image: Uri): SkinImageInfo {
        val resolver = ctx.contentResolver
        val mime = resolver.getType(image).orEmpty()
        val bytes = resolver.query(image, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(image)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        return SkinImageInfo(
            pngOrWebp = mime == "image/png" || mime == "image/webp",
            bytes = bytes,
            width = bounds.outWidth,
            height = bounds.outHeight,
        )
    }

    override fun writePng(image: Uri, maxWidth: Int, dest: File) {
        val bitmap = ctx.contentResolver.openInputStream(image)?.use { BitmapFactory.decodeStream(it) }
            ?: error("No se pudo leer la imagen.")
        val scale = maxWidth.toFloat() / bitmap.width
        val scaled = if (scale >= 1f) bitmap else Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt(),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
        dest.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}