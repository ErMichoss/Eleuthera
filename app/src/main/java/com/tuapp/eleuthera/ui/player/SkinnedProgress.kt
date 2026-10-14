package com.tuapp.eleuthera.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tuapp.eleuthera.data.settings.LoadedSkin
import com.tuapp.eleuthera.domain.model.TrackMode
import kotlin.math.roundToInt

private val TrackHeight = 12.dp
private val ThumbSize = 32.dp

/**
 * Barra de progreso dibujada con las imágenes del diseño elegido. Las partes sin imagen
 * usan los colores del tema. [onScrub] recibe la fracción (0..1) mientras se arrastra.
 */
@Composable
fun SkinnedSeekBar(
    fraction: Float,
    skin: LoadedSkin,
    isPlaying: Boolean,
    onScrub: (Float) -> Unit,
    onScrubEnd: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scrub by rememberUpdatedState(onScrub)
    val scrubEnd by rememberUpdatedState(onScrubEnd)
    val playedColor = MaterialTheme.colorScheme.primary
    val remainingColor = MaterialTheme.colorScheme.surfaceVariant

    // Giro del indicador: avanza mientras suena y se queda quieto en pausa.
    // Si se desactiva el giro, vuelve a su posición original por el camino más corto.
    val angle = remember { Animatable(0f) }
    LaunchedEffect(isPlaying, skin.skin.spinThumb) {
        // Se normaliza a (-180, 180] para no arrastrar vueltas acumuladas.
        var current = angle.value % 360f
        if (current > 180f) current -= 360f
        if (current <= -180f) current += 360f
        angle.snapTo(current)

        if (!skin.skin.spinThumb) {
            angle.animateTo(0f, tween(300))
            return@LaunchedEffect
        }
        while (isPlaying) {
            angle.animateTo(angle.value + 360f, tween(4_000, easing = LinearEasing))
        }
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(48.dp)   // zona táctil mínima, aunque la imagen sea pequeña
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val pad = ThumbSize.toPx() / 2
                fun frac(x: Float) = ((x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f)
                detectTapGestures { scrub(frac(it.x)); scrubEnd() }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val pad = ThumbSize.toPx() / 2
                fun frac(x: Float) = ((x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f)
                detectHorizontalDragGestures(
                    onDragStart = { scrub(frac(it.x)) },
                    onDragEnd = { scrubEnd() },
                    onDragCancel = { scrubEnd() },
                    onHorizontalDrag = { change, _ -> scrub(frac(change.position.x)) },
                )
            },
    ) {
        val pad = ThumbSize.toPx() / 2
        val trackH = TrackHeight.toPx()
        val top = (size.height - trackH) / 2
        val width = size.width - 2 * pad
        drawSkinTrack(
            fraction = fraction.coerceIn(0f, 1f),
            skin = skin,
            left = pad,
            top = top,
            width = width,
            height = trackH,
            playedColor = playedColor,
            remainingColor = remainingColor,
        )

        // Indicador
        val cx = pad + width * fraction.coerceIn(0f, 1f)
        val cy = size.height / 2
        val thumb = skin.thumb
        if (thumb != null) {
            val s = ThumbSize.toPx()
            rotate(angle.value % 360f, pivot = Offset(cx, cy)) {
                drawImage(
                    image = thumb,
                    dstOffset = IntOffset((cx - s / 2).roundToInt(), (cy - s / 2).roundToInt()),
                    dstSize = IntSize(s.roundToInt(), s.roundToInt()),
                )
            }
        } else {
            drawCircle(playedColor, radius = 10.dp.toPx(), center = Offset(cx, cy))
        }
    }
}

/** Solo la barra, sin indicador ni gestos (mini-reproductor). */
@Composable
fun SkinnedTrack(fraction: Float, skin: LoadedSkin, modifier: Modifier = Modifier) {
    val playedColor = MaterialTheme.colorScheme.primary
    val remainingColor = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier.fillMaxWidth().height(6.dp)) {
        drawSkinTrack(
            fraction = fraction.coerceIn(0f, 1f), skin = skin,
            left = 0f, top = 0f, width = size.width, height = size.height,
            playedColor = playedColor, remainingColor = remainingColor, rounded = false,
        )
    }
}

private val Desaturate = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

private fun DrawScope.drawSkinTrack(
    fraction: Float,
    skin: LoadedSkin,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    playedColor: Color,
    remainingColor: Color,
    rounded: Boolean = true,
) {
    val split = left + width * fraction
    val right = left + width
    val path = Path().apply {
        val r = if (rounded) height / 2 else 0f
        addRoundRect(RoundRect(left, top, right, top + height, CornerRadius(r)))
    }

    clipPath(path) {
        val mode = skin.skin.mode
        val played = skin.played

        if (mode == TrackMode.REVEAL && played != null) {
            // Una sola imagen a lo ancho: apagada y en gris lo pendiente, a todo color lo escuchado.
            clipRect(split, top, right, top + height) {
                drawStretched(played, left, top, width, height, alpha = 0.35f, filter = Desaturate)
            }
            clipRect(left, top, split, top + height) {
                drawStretched(played, left, top, width, height)
            }
            return@clipPath
        }

        // Parte pendiente
        clipRect(split, top, right, top + height) {
            val img = skin.remaining
            if (img == null) drawRect(remainingColor, Offset(left, top), Size(width, height))
            else drawPart(img, mode, left, top, width, height)
        }
        // Parte reproducida
        clipRect(left, top, split, top + height) {
            if (played == null) drawRect(playedColor, Offset(left, top), Size(width, height))
            else drawPart(played, mode, left, top, width, height)
        }
    }
}

private fun DrawScope.drawPart(
    img: ImageBitmap, mode: TrackMode, left: Float, top: Float, width: Float, height: Float,
) {
    if (mode == TrackMode.STRETCH) {
        drawStretched(img, left, top, width, height)
        return
    }
    // Mosaico: la imagen se escala a la altura de la barra y se repite en horizontal.
    val scale = height / img.height
    translate(left, top) {
        val shader = ImageShader(img, TileMode.Repeated, TileMode.Clamp)
        val brush = ShaderBrush(shader)
        drawContext.canvas.save()
        drawContext.canvas.scale(scale, scale)
        drawRect(brush, Offset.Zero, Size(width / scale, img.height.toFloat()))
        drawContext.canvas.restore()
    }
}

private fun DrawScope.drawStretched(
    img: ImageBitmap, left: Float, top: Float, width: Float, height: Float,
    alpha: Float = 1f, filter: ColorFilter? = null,
) {
    drawImage(
        image = img,
        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
        dstSize = IntSize(width.roundToInt().coerceAtLeast(1), height.roundToInt().coerceAtLeast(1)),
        alpha = alpha,
        colorFilter = filter,
    )
}