package com.tuapp.eleuthera.data.library

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.tuapp.eleuthera.domain.model.Track
import com.tuapp.eleuthera.platform.DesktopPaths
import com.tuapp.eleuthera.platform.FileUri
import com.tuapp.eleuthera.playback.Ffmpeg
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image

/**
 * Carátulas de las canciones en escritorio. Se sacan con ffmpeg (la incrustada en el archivo
 * o, si no hay, cover.jpg / folder.jpg de su carpeta), se reducen a 300 px y se guardan en
 * cache/covers para no volver a sacarlas. Las últimas usadas se quedan además en memoria.
 */
object Covers {
    private val dir by lazy { File(DesktopPaths.dataDir, "cache/covers").apply { mkdirs() } }

    private val memory = object : LinkedHashMap<String, ImageBitmap?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap?>) = size > 300
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(2)

    private val FOLDER_IMAGES = setOf("cover", "folder", "front", "album", "albumart")
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    /** La carátula de [track], o null si no tiene. */
    suspend fun load(track: Track): ImageBitmap? = (track.uri as? FileUri)?.file?.let { load(it) }

    /** La carátula de la canción [file], o null si no tiene. */
    suspend fun load(file: File): ImageBitmap? {
        val key = keyOf(file)
        synchronized(memory) { if (memory.containsKey(key)) return memory[key] }
        val image = withContext(io) { decode(cached(file, key)) }
        synchronized(memory) { memory[key] = image }
        return image
    }

    /** La carátula de [file] en jpg (300 px), para el estado del reproductor. */
    suspend fun bytes(file: File): ByteArray? = withContext(io) {
        runCatching { cached(file, keyOf(file))?.readBytes() }.getOrNull()
    }

    private fun keyOf(file: File) = "%016x".format(DesktopLibrary.fnv(file.path + "|" + file.lastModified()))

    private fun cached(audio: File, key: String): File? {
        val jpg = File(dir, "$key.jpg")
        if (jpg.isFile) return jpg
        // Ya se miró y no tenía: no se vuelve a lanzar ffmpeg.
        val none = File(dir, "$key.none")
        if (none.exists()) return null
        if (Ffmpeg.find() == null) return null   // sin ffmpeg no se sabe; se reintentará
        if (Ffmpeg.extractCover(audio, jpg)) return jpg
        folderImage(audio)?.let { if (Ffmpeg.extractCover(it, jpg)) return jpg }
        runCatching { none.createNewFile() }
        return null
    }

    private fun folderImage(audio: File): File? =
        audio.parentFile?.listFiles()?.firstOrNull {
            it.isFile && it.nameWithoutExtension.lowercase() in FOLDER_IMAGES &&
                    it.extension.lowercase() in IMAGE_EXTENSIONS
        }

    private fun decode(file: File?): ImageBitmap? = file?.let {
        runCatching { Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap() }.getOrNull()
    }
}