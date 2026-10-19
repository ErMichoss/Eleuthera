package com.tuapp.eleuthera.platform

import java.io.File
import java.net.URI

/** En escritorio, cualquier ubicación de archivo; la única implementación es [FileUri]. */
actual abstract class PlatformUri {
    abstract val uri: URI
    actual abstract override fun toString(): String
}

/** Archivo local (file:///...). Dos FileUri del mismo archivo son iguales. */
data class FileUri(override val uri: URI) : PlatformUri() {
    constructor(file: File) : this(file.toURI())

    override fun toString(): String = uri.toString()

    val file: File get() = File(uri)
}