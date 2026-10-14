package com.tuapp.eleuthera.data.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.tuapp.eleuthera.domain.model.ProgressSkin
import com.tuapp.eleuthera.domain.model.SkinSlot
import com.tuapp.eleuthera.domain.model.TrackMode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Diseño activo con sus imágenes ya decodificadas, listo para dibujar. */
data class LoadedSkin(
    val skin: ProgressSkin,
    val thumb: ImageBitmap?,
    val played: ImageBitmap?,
    val remaining: ImageBitmap?,
)

@Serializable
private data class SkinStore(
    /** null = el diseño por defecto de la app. */
    val activeId: String? = null,
    val skins: List<ProgressSkin> = emptyList(),
)

/**
 * Diseños de la barra de progreso guardados por el usuario. Se guardan en un JSON y sus
 * imágenes en filesDir/progress_skins/<id>/, así no dependen de que conserve las originales.
 */
@Singleton
class ProgressSkinRepository @Inject constructor(@ApplicationContext private val ctx: Context) {

    private val dir = File(ctx.filesDir, "progress_skins")
    private val storeFile = File(dir, "skins.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private val store = MutableStateFlow(load())

    val skins: Flow<List<ProgressSkin>> = store.map { it.skins }.distinctUntilChanged()
    val activeId: Flow<String?> = store.map { it.activeId }.distinctUntilChanged()

    /** Diseño activo con sus imágenes; null = barra normal. */
    val active: Flow<LoadedSkin?> = store
        .map { s -> s.skins.firstOrNull { it.id == s.activeId } }
        .distinctUntilChanged()
        .map { skin -> skin?.let(::decode) }
        .flowOn(Dispatchers.IO)

    // ---- Gestión de diseños ----

    /** Crea un diseño vacío (igual que el por defecto hasta que se le pongan imágenes) y lo activa. */
    suspend fun create(name: String): String {
        val skin = ProgressSkin(id = UUID.randomUUID().toString(), name = name.trim())
        edit { it.copy(activeId = skin.id, skins = it.skins + skin) }
        return skin.id
    }

    suspend fun rename(id: String, name: String) =
        editSkin(id) { it.copy(name = name.trim()) }

    suspend fun delete(id: String) {
        edit { s ->
            s.copy(
                activeId = s.activeId.takeUnless { it == id },
                skins = s.skins.filterNot { it.id == id },
            )
        }
        withContext(Dispatchers.IO) { File(dir, id).deleteRecursively() }
    }

    /** null = volver al diseño por defecto. */
    suspend fun setActive(id: String?) = edit { it.copy(activeId = id) }

    suspend fun setMode(id: String, mode: TrackMode) = editSkin(id) { it.copy(mode = mode) }

    suspend fun setSpin(id: String, spin: Boolean) = editSkin(id) { it.copy(spinThumb = spin) }

    // ---- Imágenes ----

    /**
     * Comprueba la imagen elegida, la reduce si hace falta y la guarda como [slot] de [id].
     * Devuelve un error con un mensaje para el usuario si no cumple los requisitos.
     */
    suspend fun setImage(id: String, slot: SkinSlot, uri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching<Unit> {
                val resolver = ctx.contentResolver
                val mime = resolver.getType(uri).orEmpty()
                require(mime == "image/png" || mime == "image/webp") {
                    "La imagen debe ser PNG o WebP."
                }
                val bytes = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
                require(bytes == null || bytes <= MAX_BYTES) { "La imagen pesa más de 1 MB." }

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val w = bounds.outWidth
                val h = bounds.outHeight
                require(w > 0 && h > 0) { "No se pudo leer la imagen." }
                validate(slot, w, h)

                val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    ?: error("No se pudo leer la imagen.")
                val scaled = downscale(bitmap, slot)

                val skinDir = File(dir, id).apply { mkdirs() }
                // Nombre nuevo en cada cambio para no mostrar la imagen anterior desde caché.
                val file = File(skinDir, "${slot.fileName}_${System.currentTimeMillis()}.png")
                file.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }

                val old = store.value.skins.firstOrNull { it.id == id }?.path(slot)
                editSkin(id) { it.withPath(slot, file.absolutePath) }
                old?.let { File(it).delete() }
                Unit
            }
        }

    suspend fun clearImage(id: String, slot: SkinSlot) {
        val old = store.value.skins.firstOrNull { it.id == id }?.path(slot)
        editSkin(id) { it.withPath(slot, null) }
        withContext(Dispatchers.IO) { old?.let { File(it).delete() } }
    }

    private fun validate(slot: SkinSlot, w: Int, h: Int) {
        when (slot) {
            SkinSlot.THUMB -> {
                require(abs(w - h) <= maxOf(w, h) / 10) { "El indicador debe ser cuadrado." }
                require(w in 64..512) { "El indicador debe medir entre 64 y 512 px de lado." }
            }
            SkinSlot.PLAYED, SkinSlot.REMAINING -> {
                require(w >= h) { "Las imágenes de las barras deben ser horizontales." }
                require(h in 16..64) { "Las barras deben medir entre 16 y 64 px de alto." }
            }
        }
    }

    /** Reduce a un tamaño razonable para dibujar sin gastar memoria. */
    private fun downscale(src: Bitmap, slot: SkinSlot): Bitmap {
        val scale = when (slot) {
            SkinSlot.THUMB -> 256f / src.width
            else -> 2048f / src.width
        }
        if (scale >= 1f) return src
        return Bitmap.createScaledBitmap(
            src, (src.width * scale).roundToInt(), (src.height * scale).roundToInt().coerceAtLeast(1), true,
        )
    }

    private fun decode(skin: ProgressSkin): LoadedSkin {
        fun img(path: String?) = path?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
        return LoadedSkin(skin, img(skin.thumb), img(skin.played), img(skin.remaining))
    }

    // ---- Persistencia ----

    private suspend fun editSkin(id: String, change: (ProgressSkin) -> ProgressSkin) =
        edit { s -> s.copy(skins = s.skins.map { if (it.id == id) change(it) else it }) }

    private suspend fun edit(change: (SkinStore) -> SkinStore) {
        mutex.withLock {
            val updated = change(store.value)
            store.value = updated
            withContext(Dispatchers.IO) {
                dir.mkdirs()
                storeFile.writeText(json.encodeToString(updated))
            }
        }
    }

    private fun load(): SkinStore = runCatching {
        json.decodeFromString<SkinStore>(storeFile.readText())
    }.getOrDefault(SkinStore())

    private companion object { const val MAX_BYTES = 1_048_576L }
}