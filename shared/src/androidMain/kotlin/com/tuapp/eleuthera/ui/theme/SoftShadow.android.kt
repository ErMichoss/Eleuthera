package com.tuapp.eleuthera.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

/** Android: Paint.setShadowLayer, lo mismo que hacía antes AppComponents. */
internal actual fun DrawScope.drawSoftShadow(
    path: Path,
    fill: Color,
    shadow: Color,
    radius: Float,
    offset: Float,
) = drawIntoCanvas { canvas ->
    val paint = android.graphics.Paint().apply {
        color = fill.toArgb()
        isAntiAlias = true
        setShadowLayer(radius, offset, offset, shadow.toArgb())
    }
    canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
}