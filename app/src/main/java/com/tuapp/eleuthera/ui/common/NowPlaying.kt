package com.tuapp.eleuthera.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tuapp.eleuthera.domain.model.Track

/** Qué canción suena ahora (su mediaId = id de la canción) y si está en pausa. */
data class NowPlaying(val mediaId: String? = null, val isPlaying: Boolean = false) {
    fun isCurrent(track: Track) = mediaId != null && mediaId == track.id.toString()
}

/**
 * Lo pone MainActivity para toda la app: así cualquier lista sabe qué canción resaltar
 * sin tener que pasarle el reproductor. Solo se recomponen las filas que lo leen.
 */
val LocalNowPlaying = compositionLocalOf { NowPlaying() }

/** Carátula de una fila; si es la canción que suena, lleva encima las barras animadas. */
@Composable
fun TrackRowArt(track: Track, size: Dp = 44.dp) {
    val now = LocalNowPlaying.current
    Box(Modifier.size(size).clip(RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
        TrackArt(track.uri, Modifier.size(size))
        if (now.isCurrent(track)) {
            Box(
                Modifier.size(size).background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                PlayingBars(playing = now.isPlaying, color = Color.White)
            }
        }
    }
}

/** Colores de una fila de canción: fondo tintado y título en color de acento si es la que suena. */
@Composable
fun trackRowColors(track: Track, base: Color = MaterialTheme.colorScheme.surface): ListItemColors {
    val current = LocalNowPlaying.current.isCurrent(track)
    val accent = MaterialTheme.colorScheme.primary
    return if (current) {
        ListItemDefaults.colors(
            containerColor = accent.copy(alpha = 0.12f).compositeOver(base),
            headlineColor = accent,
            supportingColor = accent.copy(alpha = 0.8f),
        )
    } else {
        ListItemDefaults.colors(containerColor = base)
    }
}

/** Tres barras tipo ecualizador: se mueven si suena y se quedan quietas en pausa. */
@Composable
fun PlayingBars(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.size(width = 18.dp, height = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (playing) {
            val transition = rememberInfiniteTransition(label = "bars")
            listOf(520, 380, 640).forEach { ms ->
                val h by transition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(ms, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "bar$ms",
                )
                Bar(h, color)
            }
        } else {
            listOf(0.45f, 0.7f, 0.35f).forEach { Bar(it, color) }
        }
    }
}

@Composable
private fun Bar(fraction: Float, color: Color) {
    Box(
        Modifier
            .width(4.dp)
            .fillMaxHeight(fraction)
            .clip(RoundedCornerShape(1.dp))
            .background(color)
    )
}