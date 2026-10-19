package com.tuapp.eleuthera.ui.settings

import com.tuapp.eleuthera.domain.model.AudioFormat
import com.tuapp.eleuthera.domain.model.ThemeConfig
import kotlinx.coroutines.flow.StateFlow

/** Tema de la app, para Ajustes > Apariencia. Android: ThemeViewModel; escritorio: DesktopThemeUi. */
interface ThemeUi {
    val config: StateFlow<ThemeConfig>
    fun update(transform: (ThemeConfig) -> ThemeConfig)
    fun reset() = update { ThemeConfig() }
}

/** Ajustes > Descargas. Android: DownloadSettingsViewModel; escritorio: DesktopDownloadSettingsUi. */
interface DownloadSettingsUi {
    val format: StateFlow<AudioFormat>
    fun setFormat(format: AudioFormat)
}

/**
 * Color ARGB a tono (0–360), saturación y brillo (0–1), igual que android.graphics.Color.colorToHSV,
 * que no existe en escritorio.
 */
fun colorToHsv(argb: Int): FloatArray {
    val r = (argb shr 16 and 0xFF) / 255f
    val g = (argb shr 8 and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * ((b - r) / delta + 2f)
        else -> 60f * ((r - g) / delta + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val saturation = if (max == 0f) 0f else delta / max
    return floatArrayOf(hue, saturation, max)
}

/** Lo contrario de [colorToHsv]: color opaco en ARGB. */
fun hsvToColor(hue: Float, saturation: Float, value: Float): Int {
    val h = ((hue % 360f) + 360f) % 360f
    val c = value * saturation
    val x = c * (1 - kotlin.math.abs((h / 60f) % 2f - 1))
    val m = value - c
    val (r, g, b) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    fun channel(v: Float) = ((v + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
}