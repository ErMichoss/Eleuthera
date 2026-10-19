package com.tuapp.eleuthera.data.settings

import com.tuapp.eleuthera.domain.model.ProgressSkin
import com.tuapp.eleuthera.domain.model.SkinSlot
import com.tuapp.eleuthera.domain.model.TrackMode
import com.tuapp.eleuthera.platform.PlatformUri
import com.tuapp.eleuthera.ui.platform.decodeImage
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Lo que hay que comprobar de una imagen antes de aceptarla en un diseño. */
class SkinImageInfo(
    /** PNG o WebP (las únicas con transparencia que se aceptan). */
    val pngOrWebp: Boolean,
    /** Tamaño del archivo, o null si no se sabe. */
    val bytes: Long?,
    val width: Int,
    val height: Int,
)

@Serializable
private data class SkinStore(
    /** null = el diseño por defecto de la app. */
    val activeId: String? = null,
    val skins: List<ProgressSkin> = emptyList(),
)

/**
 * Diseños de la barra de progreso guardados por el usuario. Se guardan en un JSON y sus
 * imágenes en [dir]/<id>/, así no dependen de que conserve las originales.
 *
 * Igual en Android y escritorio; cada plataforma solo pone cómo leer la imagen elegida
 * ([inspect]) y cómo guardarla reducida ([writePng]). En Android es ProgressSkinRepository;
 * en escritorio, DesktopProgressSkinStore.
 */
abstract class ProgressSkinStore(private val dir: File) {

    /** Formato, peso y medidas de [image]. Lanza excepción si no se puede leer. */
    protected abstract fun inspect(image: PlatformUri): SkinImageInfo

    /** Guarda [image] en [dest] como PNG, reducida a [maxWidth] px de ancho como mucho. */
    protected abstract fun writePng(image: PlatformUri, maxWidth: Int, dest: File)

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

    suspend fun setSideColor(id: String, color: Int) = editSkin(id) { it.copy(sideColor = color) }

    suspend fun setCenterColor(id: String, color: Int) = editSkin(id) { it.copy(centerColor = color) }

    // ---- Imágenes ----

    /**
     * Comprueba la imagen elegida, la reduce si hace falta y la guarda como [slot] de [id].
     * Devuelve un error con un mensaje para el usuario si no cumple los requisitos.
     */
    suspend fun setImage(id: String, slot: SkinSlot, image: PlatformUri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching<Unit> {
                val info = inspect(image)
                require(info.pngOrWebp) { "La imagen debe ser PNG o WebP." }
                require(info.bytes == null || info.bytes <= MAX_BYTES) { "La imagen pesa más de 1 MB." }
                require(info.width > 0 && info.height > 0) { "No se pudo leer la imagen." }
                validate(slot, info.width, info.height)

                val skinDir = File(dir, id).apply { mkdirs() }
                // Nombre nuevo en cada cambio para no mostrar la imagen anterior desde caché.
                val file = File(skinDir, "${slot.fileName}_${System.currentTimeMillis()}.png")
                val square = slot == SkinSlot.THUMB || slot == SkinSlot.END
                writePng(image, if (square) 256 else 2048, file)

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
            SkinSlot.END -> {
                require(abs(w - h) <= maxOf(w, h) / 10) { "La imagen final debe ser cuadrada." }
                require(w in 64..512) { "La imagen final debe medir entre 64 y 512 px de lado." }
            }
            SkinSlot.PLAYED, SkinSlot.REMAINING -> {
                require(w >= h) { "Las imágenes de las barras deben ser horizontales." }
                require(h in 16..64) { "Las barras deben medir entre 16 y 64 px de alto." }
            }
        }
    }

    private fun decode(skin: ProgressSkin): LoadedSkin {
        fun img(path: String?) = path?.let { runCatching { decodeImage(File(it).readBytes()) }.getOrNull() }
        return LoadedSkin(skin, img(skin.thumb), img(skin.played), img(skin.remaining), img(skin.end))
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
                storeFile.writeText(json.encodeToString(SkinStore.serializer(), updated))
            }
        }
    }

    private fun load(): SkinStore = runCatching {
        json.decodeFromString(SkinStore.serializer(), storeFile.readText())
    }.getOrDefault(SkinStore())

    private companion object { const val MAX_BYTES = 1_048_576L }
}