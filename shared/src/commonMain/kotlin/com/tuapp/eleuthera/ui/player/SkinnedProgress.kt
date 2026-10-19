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
private val EndMarkerSize = 40.dp
private val MiniThumbSize = 24.dp

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
    val sideColor = skin.skin.sideColor?.let { Color(it) } ?: playedColor
    val centerColor = skin.skin.centerColor?.let { Color(it) } ?: playedColor

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
            sideColor = sideColor,
            centerColor = centerColor,
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

        if (skin.skin.mode == TrackMode.CHASE) {
            skin.end?.let { image ->
                val size = EndMarkerSize.toPx()
                drawImage(
                    image = image,
                    dstOffset = IntOffset(
                        (pad + width - size / 2).roundToInt(),
                        (cy - size / 2).roundToInt(),
                    ),
                    dstSize = IntSize(size.roundToInt(), size.roundToInt()),
                )
            }
        }
    }
}

/**
 * Barra con la skin activa. Los toques junto a los extremos ajustan la selección; los demás
 * buscan en la previsualización sin cambiar los límites.
 */
@Composable
fun SkinnedRangeSeekBar(
    startMs: Long,
    endMs: Long,
    durationMs: Long,
    skin: LoadedSkin?,
    onRangeChange: (startMs: Long, endMs: Long) -> Unit,
    modifier: Modifier = Modifier,
    playheadMs: Long? = null,
    onPlayheadChange: ((Long) -> Unit)? = null,
    onPlayheadDragStart: (() -> Unit)? = null,
) {
    val rangeChange by rememberUpdatedState(onRangeChange)
    val playheadChange by rememberUpdatedState(onPlayheadChange)
    val playheadDragStart by rememberUpdatedState(onPlayheadDragStart)
    val currentStart by rememberUpdatedState(startMs)
    val currentEnd by rememberUpdatedState(endMs)
    val currentPlayhead by rememberUpdatedState(playheadMs)
    val playedColor = MaterialTheme.colorScheme.primary
    val remainingColor = MaterialTheme.colorScheme.surfaceVariant

    Canvas(
        modifier.fillMaxWidth().height(72.dp)
            .pointerInput(durationMs) {
                if (durationMs <= 0) return@pointerInput
                val pad = 22.dp.toPx()
                fun update(x: Float) {
                    val startX = pad + (size.width - 2 * pad) *
                        currentStart.toFloat() / durationMs.toFloat()
                    val endX = pad + (size.width - 2 * pad) *
                        currentEnd.toFloat() / durationMs.toFloat()
                    val handleRadius = 22.dp.toPx()
                    if (currentPlayhead != null && playheadChange != null &&
                        kotlin.math.abs(x - startX) > handleRadius &&
                        kotlin.math.abs(x - endX) > handleRadius
                    ) {
                        playheadChange?.invoke(positionAt(x, pad, size.width.toFloat(), durationMs))
                        return
                    }
                    val moveStart = kotlin.math.abs(x - startX) <= kotlin.math.abs(x - endX)
                    val fraction = ((x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f)
                    val value = ((fraction * durationMs.toFloat() / 1_000f).roundToInt() * 1_000L)
                        .coerceIn(0, durationMs)
                    val minimum = 1_000L.coerceAtMost(durationMs)
                    if (moveStart) {
                        rangeChange(value.coerceAtMost((currentEnd - minimum).coerceAtLeast(0)), currentEnd)
                    } else {
                        rangeChange(currentStart, value.coerceAtLeast((currentStart + minimum).coerceAtMost(durationMs)))
                    }
                }
                detectTapGestures { update(it.x) }
            }
            .pointerInput(durationMs) {
                if (durationMs <= 0) return@pointerInput
                val pad = 22.dp.toPx()
                var draggingHandle = 0
                fun chooseHandle(x: Float) {
                    val startX = pad + (size.width - 2 * pad) *
                        currentStart.toFloat() / durationMs.toFloat()
                    val endX = pad + (size.width - 2 * pad) *
                        currentEnd.toFloat() / durationMs.toFloat()
                    val startDistance = kotlin.math.abs(x - startX)
                    val endDistance = kotlin.math.abs(x - endX)
                    val headX = currentPlayhead?.let {
                        pad + (size.width - 2 * pad) * it.toFloat() / durationMs.toFloat()
                    }
                    val headDistance = headX?.let { kotlin.math.abs(x - it) }
                    val handleRadius = 22.dp.toPx()
                    val playheadRadius = 32.dp.toPx()
                    // El cabezal solo gana al inicio/fin cuando no se solapa con sus zonas táctiles.
                    draggingHandle = when {
                        headDistance != null && playheadChange != null &&
                            headDistance <= playheadRadius &&
                            startDistance > handleRadius && endDistance > handleRadius -> 2
                        headDistance != null && playheadChange != null &&
                            startDistance > handleRadius && endDistance > handleRadius -> 2
                        startDistance <= endDistance -> 0
                        else -> 1
                    }
                }
                detectHorizontalDragGestures(
                    onDragStart = {
                        chooseHandle(it.x)
                        if (draggingHandle == 2) playheadDragStart?.invoke()
                    },
                    onHorizontalDrag = { change, _ ->
                        val value = positionAt(change.position.x, pad, size.width.toFloat(), durationMs)
                        if (draggingHandle == 2) {
                            playheadChange?.invoke(value)
                        } else {
                            val minimum = 1_000L.coerceAtMost(durationMs)
                            if (draggingHandle == 0) {
                                rangeChange(value.coerceAtMost((currentEnd - minimum).coerceAtLeast(0)), currentEnd)
                            } else {
                                rangeChange(currentStart, value.coerceAtLeast((currentStart + minimum).coerceAtMost(durationMs)))
                            }
                        }
                    },
                )
            },
    ) {
        if (durationMs <= 0) return@Canvas
        val pad = 22.dp.toPx()
        val trackHeight = 20.dp.toPx()
        val top = (size.height - trackHeight) / 2
        val width = size.width - 2 * pad
        val from = (startMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        val to = (endMs.toFloat() / durationMs.toFloat()).coerceIn(from, 1f)
        if (skin != null) {
            drawSkinRange(from, to, skin, pad, top, width, trackHeight, playedColor, remainingColor)
        } else {
            drawRoundRect(
                remainingColor, Offset(pad, top), Size(width, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2),
            )
            drawRoundRect(
                playedColor, Offset(pad + width * from, top), Size(width * (to - from), trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2),
            )
        }

        val thumbSize = 40.dp.toPx()
        for (fraction in listOf(from, to)) {
            val cx = pad + width * fraction
            val cy = size.height / 2
            val thumb = skin?.thumb
            if (thumb != null) {
                drawImage(
                    image = thumb,
                    dstOffset = IntOffset((cx - thumbSize / 2).roundToInt(), (cy - thumbSize / 2).roundToInt()),
                    dstSize = IntSize(thumbSize.roundToInt(), thumbSize.roundToInt()),
                )
            } else {
                drawCircle(playedColor, radius = 11.dp.toPx(), center = Offset(cx, cy))
            }
        }

        playheadMs?.let { position ->
            val fraction = (position.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            val x = pad + width * fraction
            val barHeight = 56.dp.toPx()
            val y = (size.height - barHeight) / 2
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.75f),
                topLeft = Offset(x - 4.dp.toPx(), y),
                size = Size(8.dp.toPx(), barHeight),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(x - 2.dp.toPx(), y),
                size = Size(4.dp.toPx(), barHeight),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
    }
}

private fun positionAt(x: Float, pad: Float, width: Float, durationMs: Long): Long {
    val fraction = ((x - pad) / (width - 2 * pad)).coerceIn(0f, 1f)
    return ((fraction * durationMs.toFloat() / 1_000f).roundToInt() * 1_000L)
        .coerceIn(0, durationMs)
}

/** Solo la barra, sin indicador ni gestos (mini-reproductor). */
@Composable
fun SkinnedTrack(fraction: Float, skin: LoadedSkin, modifier: Modifier = Modifier) {
    val playedColor = MaterialTheme.colorScheme.primary
    val remainingColor = MaterialTheme.colorScheme.surfaceVariant
    val sideColor = skin.skin.sideColor?.let { Color(it) } ?: playedColor
    val centerColor = skin.skin.centerColor?.let { Color(it) } ?: playedColor
    val showEnd = skin.skin.mode == TrackMode.CHASE && skin.end != null
    val thumb = skin.thumb
    val showThumb = thumb != null
    val canvasHeight = when {
        showEnd -> EndMarkerSize
        showThumb -> MiniThumbSize
        else -> 6.dp
    }
    Canvas(modifier.fillMaxWidth().height(canvasHeight)) {
        val trackHeight = 6.dp.toPx()
        val trackTop = (size.height - trackHeight) / 2
        val endSize = if (showEnd) EndMarkerSize.toPx() else 0f
        val thumbSize = if (showThumb) MiniThumbSize.toPx() else 0f
        val trackLeft = thumbSize / 2
        val trackRight = size.width - endSize / 2
        val trackWidth = (trackRight - trackLeft).coerceAtLeast(0f)
        drawSkinTrack(
            fraction = fraction.coerceIn(0f, 1f), skin = skin,
            left = trackLeft, top = trackTop, width = trackWidth, height = trackHeight,
            playedColor = playedColor, remainingColor = remainingColor,
            sideColor = sideColor, centerColor = centerColor, rounded = false,
        )
        if (showEnd) {
            val image = requireNotNull(skin.end)
            drawImage(
                image = image,
                dstOffset = IntOffset(
                    (trackRight - endSize / 2).roundToInt(),
                    ((size.height - endSize) / 2).roundToInt(),
                ),
                dstSize = IntSize(endSize.roundToInt(), endSize.roundToInt()),
            )
        }
        if (thumb != null) {
            val thumbCenter = trackLeft + trackWidth * fraction.coerceIn(0f, 1f)
            drawImage(
                image = thumb,
                dstOffset = IntOffset(
                    (thumbCenter - thumbSize / 2).roundToInt(),
                    ((size.height - thumbSize) / 2).roundToInt(),
                ),
                dstSize = IntSize(thumbSize.roundToInt(), thumbSize.roundToInt()),
            )
        }
    }
}

private fun DrawScope.drawSkinRange(
    from: Float,
    to: Float,
    skin: LoadedSkin,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    playedColor: Color,
    remainingColor: Color,
) {
    val start = left + width * from
    val end = left + width * to
    val right = left + width
    val path = Path().apply {
        addRoundRect(RoundRect(left, top, right, top + height, CornerRadius(height / 2)))
    }
    val sideColor = skin.skin.sideColor?.let { Color(it) } ?: playedColor
    val centerColor = skin.skin.centerColor?.let { Color(it) } ?: playedColor

    clipPath(path) {
        val mode = skin.skin.mode
        if (mode == TrackMode.CHASE) {
            drawRect(sideColor, Offset(left, top), Size(width, height))
            val middleHeight = height * 0.42f
            val middleTop = top + (height - middleHeight) / 2
            drawRect(remainingColor, Offset(left, middleTop), Size(width, middleHeight))
            clipRect(start, middleTop, end, middleTop + middleHeight) {
                drawRect(centerColor, Offset(left, middleTop), Size(width, middleHeight))
            }
            return@clipPath
        }

        val played = skin.played
        if (mode == TrackMode.REVEAL && played != null) {
            drawStretched(played, left, top, width, height, alpha = 0.35f, filter = Desaturate)
            clipRect(start, top, end, top + height) {
                drawStretched(played, left, top, width, height)
            }
            return@clipPath
        }

        val remaining = skin.remaining
        if (remaining == null) drawRect(remainingColor, Offset(left, top), Size(width, height))
        else drawPart(remaining, mode, left, top, width, height)
        if (played == null) {
            clipRect(start, top, end, top + height) {
                drawRect(playedColor, Offset(left, top), Size(width, height))
            }
        } else {
            clipRect(start, top, end, top + height) {
                drawPart(played, mode, left, top, width, height)
            }
        }
    }
    if (skin.skin.mode == TrackMode.CHASE) {
        skin.end?.let { image ->
            val markerSize = EndMarkerSize.toPx()
            drawImage(
                image = image,
                dstOffset = IntOffset(
                    (end - markerSize / 2).roundToInt(),
                    (top + (height - markerSize) / 2).roundToInt(),
                ),
                dstSize = IntSize(markerSize.roundToInt(), markerSize.roundToInt()),
            )
        }
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
    sideColor: Color,
    centerColor: Color,
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

        if (mode == TrackMode.CHASE) {
            drawRect(sideColor, Offset(left, top), Size(width, height))
            val middleHeight = height * 0.42f
            val middleTop = top + (height - middleHeight) / 2
            drawRect(remainingColor, Offset(left, middleTop), Size(width, middleHeight))
            clipRect(left, middleTop, split, middleTop + middleHeight) {
                drawRect(centerColor, Offset(left, middleTop), Size(width, middleHeight))
            }
            return@clipPath
        }

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