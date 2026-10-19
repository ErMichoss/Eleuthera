package com.tuapp.eleuthera.desktop

import com.tuapp.eleuthera.data.library.Covers
import com.tuapp.eleuthera.data.library.DesktopLibrary
import com.tuapp.eleuthera.data.settings.DownloadSettings
import com.tuapp.eleuthera.data.settings.LoadedSkin
import com.tuapp.eleuthera.data.settings.ThemeRepository
import com.tuapp.eleuthera.domain.model.AudioFormat
import com.tuapp.eleuthera.domain.model.ThemeConfig
import com.tuapp.eleuthera.ui.settings.DownloadSettingsUi
import com.tuapp.eleuthera.ui.settings.ThemeUi
import com.tuapp.eleuthera.domain.model.Track
import com.tuapp.eleuthera.platform.FileUri
import com.tuapp.eleuthera.platform.PlatformUri
import com.tuapp.eleuthera.playback.DesktopPlayerController
import com.tuapp.eleuthera.playback.PlayerState
import com.tuapp.eleuthera.ui.common.NowPlaying
import com.tuapp.eleuthera.ui.library.LibraryController
import com.tuapp.eleuthera.ui.player.PlayerUi
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lo que en Android hacen los ViewModels, para las pantallas comunes en escritorio.
 * Viven mientras la app está abierta (no hay rotaciones ni procesos que maten pantallas).
 */
internal val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

/** El reproductor visto desde MiniPlayer y NowPlayingScreen. */
class DesktopPlayerUi(
    private val player: DesktopPlayerController,
    skins: Flow<LoadedSkin?>,
) : PlayerUi {

    override val state: StateFlow<PlayerState> = player.state

    override val positionMs: StateFlow<Long> = flow {
        while (true) {
            emit(player.positionMs)
            delay(500)
        }
    }.stateIn(uiScope, SharingStarted.WhileSubscribed(2_000), 0L)

    /** Diseño de la barra de progreso elegido en Ajustes; null = la barra normal. */
    override val progressSkin: StateFlow<LoadedSkin?> = skins.stateIn(uiScope, SharingStarted.Eagerly, null)

    /** Solo qué suena y si está en pausa: las listas no se recomponen por otros cambios. */
    val nowPlaying: StateFlow<NowPlaying> = player.state
        .map { NowPlaying(it.mediaId, it.isPlaying) }
        .distinctUntilChanged()
        .stateIn(uiScope, SharingStarted.Eagerly, NowPlaying())

    override fun toggle() = player.toggle()
    override fun next() = player.next()
    override fun previous() = player.previous()
    override fun seekTo(ms: Long) = player.seekTo(ms)
    override fun toggleShuffle() = player.toggleShuffle()
    override fun cycleRepeat() = player.cycleRepeat()
    override fun jumpTo(index: Int) = player.jumpTo(index)
    override fun move(from: Int, to: Int) = player.move(from, to)
    override fun remove(index: Int) = player.removeAt(index)
}

/** La biblioteca común alimentada con las canciones de las carpetas de escritorio. */
class DesktopLibraryModel(private val library: DesktopLibrary) {
    val controller = LibraryController()

    init {
        uiScope.launch {
            library.tracks.filterNotNull().collect(controller::setTracks)
        }
    }

    /** Borra el archivo del disco. null si ha ido bien; si no, el motivo. */
    suspend fun delete(track: Track): String? = withContext(Dispatchers.IO) {
        val file = (track.uri as? FileUri)?.file ?: return@withContext "No se puede borrar ${track.title}"
        if (file.exists() && !file.delete()) return@withContext "No se pudo borrar ${file.name}"
        library.refresh()
        null
    }
}

/** Carátulas en escritorio, para TrackArt (común): las saca Covers con ffmpeg. */
val desktopArtLoader: suspend (PlatformUri) -> ImageBitmap? = { uri ->
    (uri as? FileUri)?.file?.let { Covers.load(it) }
}

/** Tema de la app para Ajustes > Apariencia (común). */
class DesktopThemeUi(private val repo: ThemeRepository) : ThemeUi {
    override val config: StateFlow<ThemeConfig> =
        repo.config.stateIn(uiScope, SharingStarted.Eagerly, ThemeConfig())

    override fun update(transform: (ThemeConfig) -> ThemeConfig) {
        uiScope.launch { repo.save(transform(config.value)) }
    }
}

/** Formato de descarga para Ajustes > Descargas (común). */
class DesktopDownloadSettingsUi(private val settings: DownloadSettings) : DownloadSettingsUi {
    override val format: StateFlow<AudioFormat> =
        settings.format.stateIn(uiScope, SharingStarted.Eagerly, AudioFormat.MP3)

    override fun setFormat(format: AudioFormat) {
        uiScope.launch { settings.setFormat(format) }
    }
}