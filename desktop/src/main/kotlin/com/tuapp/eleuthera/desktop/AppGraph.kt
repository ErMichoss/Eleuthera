package com.tuapp.eleuthera.desktop

import com.tuapp.eleuthera.data.musicbrainz.MusicBrainzService
import com.tuapp.eleuthera.ui.musicbrainz.MusicBrainzController
import com.tuapp.eleuthera.data.db.AppDatabase
import com.tuapp.eleuthera.data.db.buildAppDatabase
import com.tuapp.eleuthera.data.download.DesktopDownloads
import com.tuapp.eleuthera.data.importer.DesktopImports
import com.tuapp.eleuthera.data.settings.DesktopCookieStore
import com.tuapp.eleuthera.data.update.DesktopUpdateChecker
import com.tuapp.eleuthera.data.library.DesktopLibrary
import com.tuapp.eleuthera.data.lrclib.LrclibService
import com.tuapp.eleuthera.data.playlist.DesktopPlaylistStore
import com.tuapp.eleuthera.data.settings.DesktopLibrarySettings
import com.tuapp.eleuthera.data.settings.DesktopProgressSkinStore
import com.tuapp.eleuthera.data.settings.DownloadSettings
import com.tuapp.eleuthera.data.settings.PlayerSettings
import com.tuapp.eleuthera.data.settings.PreferenceStores
import com.tuapp.eleuthera.data.settings.ThemeRepository
import com.tuapp.eleuthera.data.youtube.DesktopYtDlp
import com.tuapp.eleuthera.data.youtube.Resolved
import com.tuapp.eleuthera.domain.model.DownloadStatus
import com.tuapp.eleuthera.domain.model.RemoteTrack
import com.tuapp.eleuthera.platform.DesktopPaths
import com.tuapp.eleuthera.platform.Tools
import com.tuapp.eleuthera.playback.DesktopPlayerController
import com.tuapp.eleuthera.ui.playlists.PlaylistsController
import com.tuapp.eleuthera.ui.search.SearchBackend
import com.tuapp.eleuthera.ui.search.SearchController
import com.tuapp.eleuthera.ui.settings.ProgressSkinController
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
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
    val cookies by lazy { DesktopCookieStore(DesktopPaths.dataDir) }
    val ytDlp by lazy { DesktopYtDlp(downloadSettings, cookies) }
    val lrclib by lazy {
        LrclibService(File(DesktopPaths.dataDir, "lrclib/lyrics-cache.json"), appVersion)
    }
    val playlistStore by lazy {
        DesktopPlaylistStore(
            database.playlistDao(),
            library.tracks.filterNotNull(),
            File(DesktopPaths.dataDir, "covers"),
        )
    }
    val downloads by lazy { DesktopDownloads(ytDlp, database.downloadDao(), library, playlistStore) }
    val imports by lazy { DesktopImports(ytDlp, downloads) }

    /** Versión de la app (la pone Gradle al arrancar; "dev" si no se sabe). */
    val appVersion: String = System.getProperty("eleuthera.version") ?: "dev"
    val updates by lazy { DesktopUpdateChecker(stores, appVersion) }

    /** Modelos de las pantallas comunes (lo que en Android son ViewModels). */
    val skinStore by lazy { DesktopProgressSkinStore(File(DesktopPaths.dataDir, "progress_skins")) }
    val playerUi by lazy { DesktopPlayerUi(player, skinStore.active) }
    val libraryModel by lazy { DesktopLibraryModel(library) }
    val playlists by lazy { PlaylistsController(uiScope, playlistStore) }
    val themeUi by lazy { DesktopThemeUi(themeRepository) }
    val downloadSettingsUi by lazy { DesktopDownloadSettingsUi(downloadSettings) }
    val skins by lazy { ProgressSkinController(uiScope, skinStore) }
    // Explorador de MusicBrainz (el mismo que en Android). Sin claves: en escritorio no se inicia sesión.
    val musicBrainz by lazy { MusicBrainzController(uiScope, MusicBrainzService(appVersion = appVersion)) }
    val search by lazy {
        SearchController(
            uiScope,
            object : SearchBackend {
                override suspend fun resolve(target: String): Resolved = ytDlp.resolve(target)
                override fun downloads(): Flow<Map<String, DownloadStatus>> = downloads.statuses
                override suspend fun enqueue(track: RemoteTrack, playlist: String?, index: Int) =
                    downloads.enqueue(track, playlist, index)
                override fun cancel(videoId: String) = downloads.cancel(videoId)
            },
        )
    }

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
