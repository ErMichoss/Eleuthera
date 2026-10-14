package com.tuapp.eleuthera.domain.model

import kotlinx.serialization.Serializable

/** Cómo se dibujan las imágenes de las barras. */
@Serializable
enum class TrackMode(val label: String) {
    /** La imagen se repite en horizontal. */
    TILE("Mosaico"),
    /** La imagen se estira a lo ancho de la barra. */
    STRETCH("Estirar"),
    /** Una sola imagen de barra completa: en color lo escuchado, apagado lo pendiente. */
    REVEAL("Revelar"),
}

/** Las tres imágenes que se pueden personalizar. */
enum class SkinSlot(val label: String, val fileName: String) {
    THUMB("Indicador", "thumb"),
    PLAYED("Parte reproducida", "played"),
    REMAINING("Parte pendiente", "remaining"),
}

/**
 * Diseño guardado de la barra de progreso. Cada imagen es la ruta de una copia en el
 * almacenamiento privado de la app; null = esa parte usa el diseño normal del tema.
 */
@Serializable
data class ProgressSkin(
    val id: String,
    val name: String,
    val thumb: String? = null,
    val played: String? = null,
    val remaining: String? = null,
    val mode: TrackMode = TrackMode.TILE,
    /** Girar el indicador mientras suena la música. */
    val spinThumb: Boolean = false,
) {
    fun path(slot: SkinSlot): String? = when (slot) {
        SkinSlot.THUMB -> thumb
        SkinSlot.PLAYED -> played
        SkinSlot.REMAINING -> remaining
    }

    fun withPath(slot: SkinSlot, path: String?): ProgressSkin = when (slot) {
        SkinSlot.THUMB -> copy(thumb = path)
        SkinSlot.PLAYED -> copy(played = path)
        SkinSlot.REMAINING -> copy(remaining = path)
    }
}