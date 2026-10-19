package com.tuapp.eleuthera.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.FontHinting
import androidx.compose.ui.text.FontRasterizationSettings
import androidx.compose.ui.text.FontSmoothing
import androidx.compose.ui.text.PlatformParagraphStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import com.tuapp.eleuthera.platform.DesktopPaths

/**
 * Texto nítido en Windows. Compose Desktop suaviza allí la letra solo en escala de grises
 * (FontRasterizationSettings.PlatformDefault: lo dejaron así "temporalmente"), y al lado del
 * resto de Windows, que usa ClearType, se ve dentado. Aquí se activa el suavizado por
 * subpíxeles, como ClearType. En Linux y macOS se deja lo que trae Compose.
 */
@OptIn(ExperimentalTextApi::class)
private val windowsText: PlatformTextStyle? =
    if (!DesktopPaths.isWindows) null
    else PlatformTextStyle(
        spanStyle = null,
        paragraphStyle = PlatformParagraphStyle(
            FontRasterizationSettings(
                smoothing = FontSmoothing.SubpixelAntiAlias,
                hinting = FontHinting.Normal,
                subpixelPositioning = true,
                autoHintingForced = false,
            )
        ),
    )

/** Aplica [windowsText] a todos los estilos de texto del tema (va dentro de AppTheme). */
@Composable
fun CrispText(content: @Composable () -> Unit) {
    val platform = windowsText ?: return content()
    val t = MaterialTheme.typography
    fun TextStyle.crisp() = merge(TextStyle(platformStyle = platform))
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        shapes = MaterialTheme.shapes,
        typography = Typography(
            displayLarge = t.displayLarge.crisp(),
            displayMedium = t.displayMedium.crisp(),
            displaySmall = t.displaySmall.crisp(),
            headlineLarge = t.headlineLarge.crisp(),
            headlineMedium = t.headlineMedium.crisp(),
            headlineSmall = t.headlineSmall.crisp(),
            titleLarge = t.titleLarge.crisp(),
            titleMedium = t.titleMedium.crisp(),
            titleSmall = t.titleSmall.crisp(),
            bodyLarge = t.bodyLarge.crisp(),
            bodyMedium = t.bodyMedium.crisp(),
            bodySmall = t.bodySmall.crisp(),
            labelLarge = t.labelLarge.crisp(),
            labelMedium = t.labelMedium.crisp(),
            labelSmall = t.labelSmall.crisp(),
        ),
        content = content,
    )
}
