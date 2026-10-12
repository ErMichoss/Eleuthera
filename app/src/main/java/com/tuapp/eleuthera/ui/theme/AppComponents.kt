package com.tuapp.eleuthera.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tuapp.eleuthera.domain.model.ComponentStyle

/**
 * Tarjeta de la app. Las pantallas usan esta en lugar de Card para que el estilo elegido
 * en Ajustes (plano, elevado, contorno, neumórfico) se aplique en todas partes.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val scheme = MaterialTheme.colorScheme
    val style = LocalComponentStyle.current

    val outer = when (style) {
        ComponentStyle.NEUMORPHIC -> modifier.neumorphic(scheme.background, shape)
        else -> modifier
    }
    Surface(
        modifier = outer,
        shape = shape,
        color = when (style) {
            ComponentStyle.FLAT -> scheme.surfaceContainer
            ComponentStyle.ELEVATED -> scheme.surfaceContainerLow
            ComponentStyle.OUTLINED, ComponentStyle.NEUMORPHIC -> scheme.background
        },
        shadowElevation = if (style == ComponentStyle.ELEVATED) 6.dp else 0.dp,
        border = if (style == ComponentStyle.OUTLINED) BorderStroke(1.dp, scheme.outline) else null,
    ) {
        Column(
            modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            content = content,
        )
    }
}

/** Botón principal de la app, con el estilo elegido en Ajustes. */
@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.small
    when (LocalComponentStyle.current) {
        ComponentStyle.FLAT -> Button(onClick, modifier, enabled, shape, content = content)
        ComponentStyle.ELEVATED -> ElevatedButton(onClick, modifier, enabled, shape, content = content)
        ComponentStyle.OUTLINED -> OutlinedButton(onClick, modifier, enabled, shape, content = content)
        ComponentStyle.NEUMORPHIC -> {
            val scheme = MaterialTheme.colorScheme
            Row(
                modifier
                    .alpha(if (enabled) 1f else 0.4f)
                    .neumorphic(scheme.background, shape, depth = 4.dp)
                    .clip(shape)
                    .background(scheme.background)
                    .clickable(enabled = enabled, onClick = onClick)
                    .defaultMinSize(minHeight = 40.dp)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompositionLocalProvider(LocalContentColor provides scheme.primary) {
                    ProvideTextStyle(MaterialTheme.typography.labelLarge) { content() }
                }
            }
        }
    }
}

/**
 * Neumorfismo: el elemento tiene el mismo color que el fondo y se distingue por dos sombras,
 * una clara arriba a la izquierda y otra oscura abajo a la derecha.
 */
fun Modifier.neumorphic(base: Color, shape: Shape, depth: Dp = 6.dp): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }.asAndroidPath()
    val d = depth.toPx()
    fun shadow(color: Color, offset: Float) = drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            this.color = base.toArgb()
            setShadowLayer(d * 1.5f, offset, offset, color.toArgb())
        }
        canvas.nativeCanvas.drawPath(path, paint)
    }
    shadow(lerp(base, Color.White, 0.18f), -d)
    shadow(lerp(base, Color.Black, 0.40f), d)
}
