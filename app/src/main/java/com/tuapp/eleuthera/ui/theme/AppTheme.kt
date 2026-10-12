package com.tuapp.eleuthera.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tuapp.eleuthera.R
import com.tuapp.eleuthera.domain.model.AppFont
import com.tuapp.eleuthera.domain.model.ComponentStyle
import com.tuapp.eleuthera.domain.model.ThemeConfig

/** Estilo de botones y tarjetas elegido; lo leen AppCard y AppButton. */
val LocalComponentStyle = staticCompositionLocalOf { ComponentStyle.FLAT }

/**
 * Fuente variable: un solo archivo .ttf contiene todos los grosores, y aquí se declaran
 * los que usa Material (normal, medio, seminegrita y negrita).
 */
@OptIn(ExperimentalTextApi::class)
private fun variableFont(resId: Int) = FontFamily(
    listOf(400, 500, 600, 700).map { weight ->
        Font(
            resId = resId,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
        )
    }
)

/** Color de texto legible (negro o blanco) sobre [color]. */
private fun on(color: Color): Color = if (color.luminance() > 0.5f) Color.Black else Color.White

/**
 * Tema de toda la app, derivado de [config]. A partir de los cuatro colores del usuario se
 * calculan los demás (superficies, contenedores, textos) para que siempre haya contraste.
 */
@Composable
fun AppTheme(config: ThemeConfig, content: @Composable () -> Unit) {
    val bg = Color(config.background)
    val primary = Color(config.primary)
    val secondary = Color(config.secondary)
    val accent = Color(config.accent)
    val onBg = on(bg)
    fun tone(amount: Float) = lerp(bg, onBg, amount)   // fondo aclarado/oscurecido

    val base = if (bg.luminance() > 0.5f) lightColorScheme() else darkColorScheme()
    val colors = base.copy(
        primary = primary, onPrimary = on(primary),
        primaryContainer = lerp(bg, primary, 0.30f), onPrimaryContainer = onBg,
        secondary = secondary, onSecondary = on(secondary),
        secondaryContainer = lerp(bg, secondary, 0.35f), onSecondaryContainer = onBg,
        tertiary = accent, onTertiary = on(accent),
        tertiaryContainer = lerp(bg, accent, 0.30f), onTertiaryContainer = onBg,
        background = bg, onBackground = onBg,
        surface = bg, onSurface = onBg,
        surfaceVariant = tone(0.12f), onSurfaceVariant = onBg.copy(alpha = 0.7f),
        surfaceTint = primary,
        surfaceDim = bg, surfaceBright = tone(0.14f),
        surfaceContainerLowest = bg,
        surfaceContainerLow = tone(0.04f),
        surfaceContainer = tone(0.07f),
        surfaceContainerHigh = tone(0.10f),
        surfaceContainerHighest = tone(0.13f),
        outline = onBg.copy(alpha = 0.45f),
        outlineVariant = onBg.copy(alpha = 0.18f),
    )

    val family = when (config.font) {
        AppFont.SANS -> FontFamily.SansSerif
        AppFont.SERIF -> FontFamily.Serif
        AppFont.MONO -> FontFamily.Monospace
        AppFont.CURSIVE -> FontFamily.Cursive
        AppFont.INTER -> variableFont(R.font.inter)
        AppFont.MONTSERRAT -> variableFont(R.font.montserrat)
        AppFont.NUNITO -> variableFont(R.font.nunito)
        AppFont.PLAYFAIR -> variableFont(R.font.playfair)
    }
    val t = Typography()
    val typography = Typography(
        displayLarge = t.displayLarge.copy(fontFamily = family),
        displayMedium = t.displayMedium.copy(fontFamily = family),
        displaySmall = t.displaySmall.copy(fontFamily = family),
        headlineLarge = t.headlineLarge.copy(fontFamily = family),
        headlineMedium = t.headlineMedium.copy(fontFamily = family),
        headlineSmall = t.headlineSmall.copy(fontFamily = family),
        titleLarge = t.titleLarge.copy(fontFamily = family),
        titleMedium = t.titleMedium.copy(fontFamily = family),
        titleSmall = t.titleSmall.copy(fontFamily = family),
        bodyLarge = t.bodyLarge.copy(fontFamily = family),
        bodyMedium = t.bodyMedium.copy(fontFamily = family),
        bodySmall = t.bodySmall.copy(fontFamily = family),
        labelLarge = t.labelLarge.copy(fontFamily = family),
        labelMedium = t.labelMedium.copy(fontFamily = family),
        labelSmall = t.labelSmall.copy(fontFamily = family),
    )

    val r = config.cornerRadiusDp.dp
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(r / 4),
        small = RoundedCornerShape(r / 2),
        medium = RoundedCornerShape(r),
        large = RoundedCornerShape(r * 1.5f),
        extraLarge = RoundedCornerShape(r * 2),
    )

    CompositionLocalProvider(LocalComponentStyle provides config.style) {
        MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
    }
}
