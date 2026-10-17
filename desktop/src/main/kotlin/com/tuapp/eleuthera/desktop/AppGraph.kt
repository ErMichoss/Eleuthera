package com.tuapp.eleuthera.desktop

import com.tuapp.eleuthera.data.db.AppDatabase
import com.tuapp.eleuthera.data.db.buildAppDatabase
import com.tuapp.eleuthera.data.download.DesktopDownloads
import com.tuapp.eleuthera.data.library.DesktopLibrary
import com.tuapp.eleuthera.data.settings.DesktopLibrarySettings
import com.tuapp.eleuthera.data.settings.DownloadSettings
import com.tuapp.eleuthera.data.settings.PlayerSettings
import com.tuapp.eleuthera.data.settings.PreferenceStores
import com.tuapp.eleuthera.data.settings.ThemeRepository
import com.tuapp.eleuthera.data.youtube.DesktopYtDlp
import com.tuapp.eleuthera.platform.DesktopPaths
import com.tuapp.eleuthera.platform.Tools
import com.tuapp.eleuthera.playback.DesktopPlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Las piezas de la app de escritorio, creadas una sola vez. Hace lo que Hilt hace en Android
 * (AppModule): aquí son pocas, así que se montan a mano sin librería de inyección.
 */
object AppGraph {
    val stores by lazy { PreferenceStores(DesktopPaths.dataDir) }
    val themeRepository by lazy { ThemeRepository(stores) }
    val downloadSettings by lazy { DownloadSettings(stores) }
    val database: AppDatabase by lazy { buildAppDatabase() }
    val playerSettings by lazy { PlayerSettings(stores) }
    val librarySettings by lazy { DesktopLibrarySettings(stores) }
    val library by lazy { DesktopLibrary(librarySettings, database.folderTrackDao()) }
    val ytDlp by lazy { DesktopYtDlp(downloadSettings) }
    val downloads by lazy { DesktopDownloads(ytDlp, database.downloadDao(), library) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Instala lo que falte (yt-dlp, ffmpeg, Deno) y actualiza yt-dlp. Se llama al arrancar
     * y desde "Reintentar". Al terminar se relee la biblioteca, por si faltaba ffmpeg.
     */
    fun prepareTools() {
        scope.launch {
            Tools.ensureInstalled()
            library.refresh()
        }
    }

    /** Arranca con el volumen que se dejó la última vez. */
    val player by lazy {
        DesktopPlayerController(initialVolume = runBlocking { playerSettings.volume.first() })
    }
}