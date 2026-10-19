package com.tuapp.eleuthera.data.settings

import com.tuapp.eleuthera.platform.FileUri
import com.tuapp.eleuthera.platform.PlatformUri
import java.io.File
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

/**
 * Diseños de la barra de progreso en escritorio: todo es [ProgressSkinStore]; aquí las
 * imágenes se leen y se reducen con Skia (lo que ya usa Compose para dibujar).
 */
class DesktopProgressSkinStore(dir: File) : ProgressSkinStore(dir) {

    override fun inspect(image: PlatformUri): SkinImageInfo {
        val file = fileOf(image)
        val decoded = Image.makeFromEncoded(file.readBytes())
        return SkinImageInfo(
            pngOrWebp = file.extension.lowercase() in setOf("png", "webp"),
            bytes = file.length(),
            width = decoded.width,
            height = decoded.height,
        )
    }

    override fun writePng(image: PlatformUri, maxWidth: Int, dest: File) {
        val source = Image.makeFromEncoded(fileOf(image).readBytes())
        val scale = minOf(1f, maxWidth.toFloat() / source.width)
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        val surface = Surface.makeRasterN32Premul(w, h)
        surface.canvas.drawImageRect(source, Rect.makeWH(w.toFloat(), h.toFloat()))
        val png = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)
            ?: error("No se pudo guardar la imagen.")
        dest.writeBytes(png.bytes)
    }

    private fun fileOf(image: PlatformUri): File =
        (image as? FileUri)?.file ?: error("No se pudo leer la imagen.")
}