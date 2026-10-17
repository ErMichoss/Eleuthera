package com.tuapp.eleuthera.platform

/**
 * Dónde está un archivo de música. En Android es android.net.Uri (content://...) y en
 * escritorio una [FileUri] con la ruta del archivo. El código común solo la guarda y la
 * compara; abrirla o reproducirla lo hace cada plataforma.
 *
 * Es abstracta, y con toString() abstracto, porque android.net.Uri es así y Kotlin exige
 * que la declaración común coincida exactamente con la de cada plataforma.
 */
expect abstract class PlatformUri {
    abstract override fun toString(): String
}