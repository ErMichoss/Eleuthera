package com.tuapp.eleuthera.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Rellena [path] con [fill] y le pone debajo una sombra difuminada de color [shadow],
 * desplazada [offset] píxeles en diagonal. Es la base del estilo neumórfico.
 *
 * Compose no tiene sombras difuminadas de color en común, así que cada plataforma usa la
 * suya: android.graphics.Paint en Android y Skia en escritorio.
 */
internal expect fun DrawScope.drawSoftShadow(
    path: Path,
    fill: Color,
    shadow: Color,
    radius: Float,
    offset: Float,
)