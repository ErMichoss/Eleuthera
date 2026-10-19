package com.tuapp.eleuthera.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/** Cómo se dibujan las imágenes de las barras. */
@Serializable
enum class TrackMode(val label: String, val description: String) {
    /** La imagen se repite en horizontal. */
    TILE(
        "Mosaico",
        "Mosaico repite la imagen una y otra vez a lo largo de la barra, sin deformarla. " +
                "Va bien con patrones pequeños: puntos, rayas, píxeles…",
    ),
    /** La imagen se estira a lo ancho de la barra. */
    STRETCH(
        "Estirar",
        "Estirar ajusta la imagen al ancho de la barra, aunque se deforme. " +
                "Va bien con degradados o diseños hechos a la medida de la barra.",
    ),
    /** Una sola imagen de barra completa: en color lo escuchado, apagado lo pendiente. */
    REVEAL(
        "Revelar",
        "Revelar usa una sola imagen de barra completa: se ve a color lo escuchado y apagado lo pendiente.",
    ),
    /** Una imagen en el puntero busca a una al final mientras que la barra tiene colores laterales e interior*/
    @SerialName("ACCENTED")
    CHASE(
        "Perseguir",
        "Perseguir usa los colores elegidos en los bordes y el centro de la barra, con una imagen fija al final.",
    ),
}

/** Las cuatro imágenes que se pueden personalizar. */
enum class SkinSlot(val label: String, val fileName: String) {
    THUMB("Indicador", "thumb"),
    PLAYED("Parte reproducida", "played"),
    REMAINING("Parte pendiente", "remaining"),
    END("Imagen final", "end"),
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
    val end: String? = null,
    val mode: TrackMode = TrackMode.TILE,
    /** Girar el indicador mientras suena la música. */
    val spinThumb: Boolean = false,
    val sideColor: Int? = null,
    val centerColor: Int? = null,
) {
    fun path(slot: SkinSlot): String? = when (slot) {
        SkinSlot.THUMB -> thumb
        SkinSlot.PLAYED -> played
        SkinSlot.REMAINING -> remaining
        SkinSlot.END -> end
    }

    fun withPath(slot: SkinSlot, path: String?): ProgressSkin = when (slot) {
        SkinSlot.THUMB -> copy(thumb = path)
        SkinSlot.PLAYED -> copy(played = path)
        SkinSlot.REMAINING -> copy(remaining = path)
        SkinSlot.END -> copy(end = path)
    }
}