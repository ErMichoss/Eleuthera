package com.tuapp.eleuthera.platform

import java.io.File

/**
 * Carpetas de la app de escritorio, donde cada sistema espera encontrarlas:
 * - Windows: %APPDATA%\Eleuthera
 * - macOS:   ~/Library/Application Support/Eleuthera
 * - Linux:   ~/.local/share/eleuthera (o $XDG_DATA_HOME/eleuthera)
 */
object DesktopPaths {
    private val os = System.getProperty("os.name").lowercase()
    private val home = File(System.getProperty("user.home"))

    val isWindows = os.contains("win")
    val isMac = os.contains("mac")

    /** Base de datos, ajustes, binarios de yt-dlp/ffmpeg... */
    val dataDir: File by lazy {
        val dir = when {
            isWindows -> File(System.getenv("APPDATA") ?: File(home, "AppData/Roaming").path, "Eleuthera")
            isMac -> File(home, "Library/Application Support/Eleuthera")
            else -> File(System.getenv("XDG_DATA_HOME") ?: File(home, ".local/share").path, "eleuthera")
        }
        dir.apply { mkdirs() }
    }

    /** Música descargada: la carpeta Música del usuario, igual que Music/Eleuthera en Android. */
    val musicDir: File by lazy {
        File(File(home, "Music"), "Eleuthera").apply { mkdirs() }
    }
}