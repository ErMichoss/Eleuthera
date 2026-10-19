package com.tuapp.eleuthera.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asSkiaPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Paint

/**
 * Escritorio: Skia dibuja la forma con un filtro de sombra. Skia mide el difuminado en
 * "sigma" y Android en radio; sigma ≈ radio / 2 da el mismo aspecto.
 */
internal actual fun DrawScope.drawSoftShadow(
    path: Path,
    fill: Color,
    shadow: Color,
    radius: Float,
    offset: Float,
) = drawIntoCanvas { canvas ->
    val sigma = radius / 2f
    val paint = Paint().apply {
        color = fill.toArgb()
        isAntiAlias = true
        imageFilter = ImageFilter.makeDropShadow(offset, offset, sigma, sigma, shadow.toArgb())
    }
    canvas.nativeCanvas.drawPath(path.asSkiaPath(), paint)
}