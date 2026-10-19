package com.tuapp.eleuthera.ui.musicbrainz

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tuapp.eleuthera.data.musicbrainz.Media
import com.tuapp.eleuthera.data.musicbrainz.MusicBrainzService
import com.tuapp.eleuthera.data.musicbrainz.Recording
import com.tuapp.eleuthera.data.musicbrainz.Release
import com.tuapp.eleuthera.data.musicbrainz.Track
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class SearchType { RECORDING, RELEASE }

/**
 * Explorador de MusicBrainz: buscar canciones o álbumes y ver sus metadatos. Igual en Android
 * (MusicBrainzViewModel lo envuelve) y escritorio (AppGraph). El estado es de Compose, así que
 * la pantalla lo lee directamente.
 */
class MusicBrainzController(
    private val scope: CoroutineScope,
    private val service: MusicBrainzService,
) {
    enum class TrackSyncStatus { PENDING, SYNCED, ERROR }

    var searchType by mutableStateOf(SearchType.RECORDING)
    var titleInput by mutableStateOf("")
    var artistInput by mutableStateOf("")
    var limitInput by mutableStateOf("25")
    var showTrackNumber by mutableStateOf(true)

    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    /** Aviso breve para la pantalla (Toast en Android, snackbar en escritorio). */
    var notice by mutableStateOf<String?>(null)

    /** Se ha iniciado sesión en MusicBrainz en esta ejecución. */
    var loggedIn by mutableStateOf(false)
        private set

    var recordingsResult by mutableStateOf<List<Recording>>(emptyList())
    var releasesResult by mutableStateOf<List<Release>>(emptyList())

    // Ventana de detalles
    var isModalOpen by mutableStateOf(false)
    var selectedRecording by mutableStateOf<Recording?>(null)
    var selectedRelease by mutableStateOf<Release?>(null)
    var selectedImageTitle by mutableStateOf<String?>(null)
    var selectedImageMbid by mutableStateOf<String?>(null)

    /** Estado de sincronización de cada pista (clave: id de la pista o su título). */
    val trackSyncStatuses = mutableStateMapOf<String, TrackSyncStatus>()
    /** Pistas que se están cargando de una en una. */
    val trackLoadingStates = mutableStateMapOf<String, Boolean>()
    /** Álbum de origen, para poder volver a él desde una canción. */
    var previousRelease by mutableStateOf<Release?>(null)

    val canLogIn: Boolean get() = service.canLogIn

    /** Página de MusicBrainz donde el usuario autoriza a la app. */
    fun authorizeUrl(): String = service.authorizeUrl()

    /** Lo llama cada plataforma al recibir el código de la redirección de OAuth. */
    fun exchangeCodeForToken(code: String) {
        scope.launch {
            isLoading = true
            val success = service.exchangeCodeForToken(code)
            isLoading = false
            loggedIn = success
            errorMessage = if (success) null else "Error al autenticar la sesión con MusicBrainz."
        }
    }

    fun search() {
        if (titleInput.isBlank()) {
            errorMessage = "Por favor ingresa un título para la búsqueda."
            return
        }
        errorMessage = null
        isLoading = true

        scope.launch {
            try {
                val limit = limitInput.toIntOrNull() ?: 25
                if (searchType == SearchType.RECORDING) {
                    val query = buildString {
                        append("recording:\"").append(escapeLucene(titleInput)).append("\" AND video:false")
                        if (artistInput.isNotBlank()) {
                            append(" AND artistname:\"").append(escapeLucene(artistInput)).append("\"")
                        }
                    }
                    recordingsResult = service.searchRecordings(query, limit).recordings ?: emptyList()
                    releasesResult = emptyList()
                } else {
                    val query = buildString {
                        append("release:\"").append(escapeLucene(titleInput)).append("\"")
                        if (artistInput.isNotBlank()) {
                            append(" AND artistname:\"").append(escapeLucene(artistInput)).append("\"")
                        }
                    }
                    releasesResult = service.searchReleases(query, limit).releases ?: emptyList()
                    recordingsResult = emptyList()
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Error de red al conectar con MusicBrainz."
            } finally {
                isLoading = false
            }
        }
    }

    private fun escapeLucene(str: String): String =
        str.replace(Regex("([+\\-|!(){}\\[\\]^\"~*?:\\\\/])")) { "\\${it.value}" }

    fun openRecordingModal(recording: Recording, mbid: String?) {
        selectedRecording = recording
        selectedRelease = null
        selectedImageTitle = recording.title
        selectedImageMbid = mbid
        isModalOpen = true

        val id = recording.id ?: return
        scope.launch {
            try {
                delay(1200.milliseconds) // Límite de MusicBrainz
                val detailed = service.getRecordingDetails(id)
                selectedRecording = mergeBasic(recording, detailed)
            } catch (e: Exception) {
                notice = "Error: ${e.message ?: "desconocido"}. Usando metadatos básicos."
            }
        }
    }

    fun openReleaseModal(release: Release) {
        selectedRelease = release
        selectedRecording = null
        selectedImageTitle = release.title
        selectedImageMbid = release.id
        isModalOpen = true

        val id = release.id ?: return
        scope.launch {
            try {
                delay(1200.milliseconds)
                val detailed = service.getReleaseDetails(id)
                selectedRelease = release.copy(
                    date = detailed.date ?: release.date,
                    country = detailed.country ?: release.country,
                    status = detailed.status ?: release.status,
                    trackCount = detailed.trackCount ?: release.trackCount,
                    artistCredit = detailed.artistCredit ?: release.artistCredit,
                    media = detailed.media ?: release.media,
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadAllTracksDetails() {
        val currentRelease = selectedRelease ?: return
        scope.launch {
            val mediaList = currentRelease.media ?: return@launch
            val updatedMediaList = mutableListOf<Media>()
            for (mediaItem in mediaList) {
                val updatedTracks = mediaItem.tracks.orEmpty().map { loadSingleTrackDetail(it) }
                updatedMediaList.add(mediaItem.copy(tracks = updatedTracks))
            }
            selectedRelease = selectedRelease?.copy(media = updatedMediaList)
        }
    }

    private suspend fun loadSingleTrackDetail(track: Track): Track {
        val recordingId = track.recording?.id ?: return track
        val trackKey = track.id ?: track.title ?: return track
        return try {
            delay(1200.milliseconds) // Límite de MusicBrainz
            val detailed = service.getRecordingDetails(recordingId)
            // El álbum abierto (selectedRelease) da el contexto: número de pista y disco.
            val enriched = mergeWithParent(track.recording ?: Recording(), detailed, parentRelease = selectedRelease)
            trackSyncStatuses[trackKey] = TrackSyncStatus.SYNCED
            track.copy(recording = enriched)
        } catch (e: Exception) {
            trackSyncStatuses[trackKey] = TrackSyncStatus.ERROR
            track
        }
    }

    fun reloadSingleTrack(track: Track) {
        val trackKey = track.id ?: track.title ?: return
        val currentRelease = selectedRelease ?: return

        scope.launch {
            trackLoadingStates[trackKey] = true
            val updatedTrack = loadSingleTrackDetail(track)
            // Se sustituye solo la pista recargada.
            val updatedMediaList = currentRelease.media?.map { mediaItem ->
                mediaItem.copy(
                    tracks = mediaItem.tracks?.map { t ->
                        if ((t.id != null && t.id == updatedTrack.id) || t.title == updatedTrack.title) updatedTrack else t
                    }
                )
            }
            selectedRelease = currentRelease.copy(media = updatedMediaList)
            trackLoadingStates[trackKey] = false
        }
    }

    fun openRecordingModalDirectly(recording: Recording, mbid: String?, parentRelease: Release? = null) {
        previousRelease = parentRelease
        selectedRecording = recording
        selectedRelease = null // Se oculta el álbum para mostrar la canción
        selectedImageTitle = recording.title
        selectedImageMbid = mbid
        isModalOpen = true
    }

    /** Vuelve al álbum desde la ventana de una de sus canciones. */
    fun backToReleaseModal() {
        val rel = previousRelease ?: return
        selectedRelease = rel
        selectedRecording = null
        selectedImageTitle = rel.title
        selectedImageMbid = rel.id
        previousRelease = null
    }

    fun closeImageModal() {
        isModalOpen = false
        selectedRecording = null
        selectedRelease = null
        selectedImageMbid = null
        selectedImageTitle = null
    }

    /** Une la grabación de la búsqueda con su detalle sin perder lo que ya había. */
    private fun mergeBasic(base: Recording, detailed: Recording): Recording {
        val oldRelease = base.releases?.firstOrNull()
        val newRelease = detailed.releases?.firstOrNull()
        // Del release viejo se mantienen los medios (pista/disco); del nuevo, fecha y estado.
        val mergedRelease = oldRelease?.copy(
            date = newRelease?.date ?: oldRelease.date,
            country = newRelease?.country ?: oldRelease.country,
            status = newRelease?.status ?: oldRelease.status,
            media = oldRelease.media ?: newRelease?.media,
        )
        return base.copy(
            genres = detailed.genres ?: base.genres,
            tagList = detailed.tagList ?: base.tagList,
            releases = mergedRelease?.let { listOf(it) } ?: base.releases,
            length = detailed.length ?: base.length,
            artistCredit = detailed.artistCredit ?: base.artistCredit,
        )
    }

    /**
     * Igual que [mergeBasic], pero con el álbum abierto como base: así la ventana de la canción
     * puede calcular su número de pista y disco.
     */
    private fun mergeWithParent(base: Recording, detailed: Recording, parentRelease: Release?): Recording {
        val oldRelease = base.releases?.firstOrNull()
        val newRelease = detailed.releases?.firstOrNull()
        val target = parentRelease ?: oldRelease ?: newRelease
        val mergedRelease = target?.copy(
            title = target.title ?: newRelease?.title ?: oldRelease?.title,
            date = parentRelease?.date ?: newRelease?.date ?: oldRelease?.date,
            country = parentRelease?.country ?: newRelease?.country ?: oldRelease?.country,
            status = parentRelease?.status ?: newRelease?.status ?: oldRelease?.status,
            media = parentRelease?.media ?: newRelease?.media ?: oldRelease?.media,
        )
        return base.copy(
            genres = detailed.genres ?: base.genres,
            tagList = detailed.tagList ?: base.tagList,
            releases = mergedRelease?.let { listOf(it) } ?: detailed.releases ?: base.releases,
            length = detailed.length ?: base.length,
            artistCredit = detailed.artistCredit ?: base.artistCredit,
        )
    }
}
