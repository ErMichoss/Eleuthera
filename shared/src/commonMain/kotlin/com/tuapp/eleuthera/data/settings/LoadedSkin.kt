package com.tuapp.eleuthera.data.settings

import androidx.compose.ui.graphics.ImageBitmap
import com.tuapp.eleuthera.domain.model.ProgressSkin

/**
 * Diseño activo de la barra de progreso con sus imágenes ya abiertas, listo para dibujar.
 *
 * Se separa de [ProgressSkin] a propósito:
 * - [ProgressSkin] solo guarda las rutas de las imágenes, porque es lo que se escribe en
 *   skins.json y se copia con cada cambio (renombrar, activar el giro...).
 * - Abrir una imagen (leerla del disco y descomprimir el PNG) es caro. Si se hiciera al
 *   pedirla, ocurriría en cada fotograma mientras se arrastra la barra o gira el indicador.
 *
 * Por eso las imágenes se abren una sola vez, en segundo plano, cuando cambia el diseño
 * activo (ver [ProgressSkinRepository.active]), y aquí solo se guardan para pintarlas.
 */
data class LoadedSkin(
    val skin: ProgressSkin,
    val thumb: ImageBitmap?,
    val played: ImageBitmap?,
    val remaining: ImageBitmap?,
)