package com.tuapp.eleuthera.domain.model

/** Las cuatro primeras son del sistema; el resto van incluidas en res/font. */
enum class AppFont(val label: String) {
    SANS("Sans"), SERIF("Serif"), MONO("Mono"), CURSIVE("Cursiva"),
    INTER("Inter"), MONTSERRAT("Montserrat"), NUNITO("Nunito"), PLAYFAIR("Playfair"),
}

enum class ComponentStyle(val label: String) {
    FLAT("Plano"), ELEVATED("Elevado"), OUTLINED("Contorno"), NEUMORPHIC("Neumórfico")
}

/** Todo lo que el usuario puede personalizar. Los colores son ARGB. */
data class ThemeConfig(
    val primary: Int = 0xFF1DB954.toInt(),
    val secondary: Int = 0xFF4F8A6B.toInt(),
    val background: Int = 0xFF121212.toInt(),
    val accent: Int = 0xFFFFB300.toInt(),
    val font: AppFont = AppFont.SANS,
    val cornerRadiusDp: Int = 16,
    val style: ComponentStyle = ComponentStyle.FLAT,
)
