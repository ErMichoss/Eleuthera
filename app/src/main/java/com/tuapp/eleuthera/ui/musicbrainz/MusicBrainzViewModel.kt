package com.tuapp.eleuthera.ui.musicbrainz

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.eleuthera.BuildConfig
import com.tuapp.eleuthera.data.musicbrainz.Media
import com.tuapp.eleuthera.data.musicbrainz.MusicBrainzService
import com.tuapp.eleuthera.data.musicbrainz.Recording
import com.tuapp.eleuthera.data.musicbrainz.Release
import com.tuapp.eleuthera.data.musicbrainz.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

enum class SearchType { RECORDING, RELEASE }

class MusicBrainzViewModel : ViewModel() {


    var searchType by mutableStateOf(SearchType.RECORDING)
    var titleInput by mutableStateOf("")
    var artistInput by mutableStateOf("")
    var limitInput by mutableStateOf("25")
    var showTrackNumber by mutableStateOf(true)

    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    var recordingsResult by mutableStateOf<List<Recording>>(emptyList())
    var releasesResult by mutableStateOf<List<Release>>(emptyList())

    // Modal de imagen ampliada
    var isModalOpen by mutableStateOf(false)
    var selectedRecording by mutableStateOf<Recording?>(null)
    var selectedRelease by mutableStateOf<Release?>(null)
    var selectedImageTitle by mutableStateOf<String?>(null)
    var selectedImageMbid by mutableStateOf<String?>(null)

    // Mapa para registrar qué IDs de elementos fallaron al cargar su carátula
    val failedImages = mutableStateMapOf<String, Boolean>()

    // Instanciamos nuestro servicio centralizado
    private val musicBrainzService = MusicBrainzService()

    enum class TrackSyncStatus {
        PENDING, SYNCED, ERROR
    }

    // Mapa para llevar el estado de sincronización de las pistas de manera aislada (Clave: trackId o identificador único, Valor: Status)
    val trackSyncStatuses = mutableStateMapOf<String, TrackSyncStatus>()
    // Mapa para registrar si una pista específica está cargando de forma individual
    val trackLoadingStates = mutableStateMapOf<String, Boolean>()
    // Guardamos temporalmente el álbum de origen para poder volver a él
    var previousRelease by mutableStateOf<Release?>(null)



    fun startOAuthLogin(context: Context) {
        val clientId = BuildConfig.MUSICBRAINZ_CLIENT_ID // El Client ID que obtienes al registrar tu app en MusicBrainz
        val redirectUri = "com.tuapp.eleuthera://oauth"

        val authUrl = "https://musicbrainz.org/oauth2/authorize".toUri().buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("scope", "tag rating") // Ajusta los permisos que necesites
            .build()

        val intent = Intent(Intent.ACTION_VIEW, authUrl)
        context.startActivity(intent)
    }

    // Función que invoca la Activity al recibir el deep link
    fun exchangeCodeForToken(code: String) {
        viewModelScope.launch {
            isLoading = true
            val success = musicBrainzService.exchangeCodeForToken(code)
            isLoading = false

            if (success) {
                errorMessage = null
                // ¡Autenticación exitosa! A partir de aquí las llamadas irán con el token del usuario.
            } else {
                errorMessage = "Error al autenticar la sesión con MusicBrainz."
            }
        }
    }

    fun markImageAsFailed(id: String) {
        failedImages[id] = true
    }

    fun search() {
        if (titleInput.isBlank()) {
            errorMessage = "Por favor ingresa un título para la búsqueda."
            return
        }
        errorMessage = null
        isLoading = true

        viewModelScope.launch {
            try {
                val limit = limitInput.toIntOrNull() ?: 25
                if (searchType == SearchType.RECORDING) {
                    val query = buildString {
                        append("recording:\"").append(escapeLucene(titleInput)).append("\" AND video:false")
                        if (artistInput.isNotBlank()) {
                            append(" AND artistname:\"").append(escapeLucene(artistInput)).append("\"")
                        }
                    }
                    val response = musicBrainzService.searchRecordings(query, limit)
                    recordingsResult = response.recordings ?: emptyList()
                    releasesResult = emptyList()
                } else {
                    val query = buildString {
                        append("release:\"").append(escapeLucene(titleInput)).append("\"")
                        if (artistInput.isNotBlank()) {
                            append(" AND artistname:\"").append(escapeLucene(artistInput)).append("\"")
                        }
                    }
                    val response = musicBrainzService.searchReleases(query, limit)
                    releasesResult = response.releases ?: emptyList()
                    recordingsResult = emptyList()
                }
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Error de red al conectar con MusicBrainz."
            } finally {
                isLoading = false
            }
        }
    }

    private fun escapeLucene(str: String): String {
        return str.replace(Regex("([+\\-|!(){}\\[\\]^\"~*?:\\\\/])")) { "\\${it.value}" }
    }

    /*
    fun openRecordingModal(context: Context, recording: Recording, mbid: String?) {
        // Mostramos inmediatamente la información básica que ya tenemos
        selectedRecording = recording
        selectedRelease = null
        selectedImageTitle = recording.title
        selectedImageMbid = mbid
        isModalOpen = true

        // Hacemos la petición de tipo Lookup en segundo plano para traer géneros y relaciones completas
        viewModelScope.launch {
            try {
                val detailedRecording = musicBrainzService.getRecordingDetails(recording.id)
                // Actualizamos el estado con la grabación enriquecida
                selectedRecording = detailedRecording
            } catch (e: Exception) {
                // Si ocurre un error de red aquí, mantenemos los datos básicos previos
                val errorMessage = e.localizedMessage ?: "Error desconocido"
                Toast.makeText(
                    context,
                    "Error: $errorMessage. Usando Metadatos reducidos (Credenciales MusicBrainz)",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    */

    fun openRecordingModal(context: Context, recording: Recording, mbid: String?) {
        selectedRecording = recording
        selectedRelease = null
        selectedImageTitle = recording.title
        selectedImageMbid = mbid
        isModalOpen = true

        viewModelScope.launch {
            try {
                delay(1200.milliseconds) // Rate limit
                val detailedRecording = musicBrainzService.getRecordingDetails(recording.id!!)

                // Aplicamos la fusión inteligente
                selectedRecording = mergeWithDetailedRecording(recording, detailedRecording)
            } catch (e: Exception) {
                val errorMessage = e.localizedMessage ?: "Error desconocido"
                Toast.makeText(
                    context,
                    "Error: $errorMessage. Usando metadatos básicos.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }


    fun openReleaseModal(context: Context, release: Release) {
        selectedRelease = release
        selectedRecording = null
        selectedImageTitle = release.title
        selectedImageMbid = release.id
        isModalOpen = true

        viewModelScope.launch {
            try {
                delay(1200.milliseconds)
                val detailedRelease = musicBrainzService.getReleaseDetails(release.id!!)

                selectedRelease = release.copy(
                    date = detailedRelease.date ?: release.date,
                    country = detailedRelease.country ?: release.country,
                    status = detailedRelease.status ?: release.status,
                    trackCount = detailedRelease.trackCount ?: release.trackCount,
                    artistCredit = detailedRelease.artistCredit ?: release.artistCredit,
                    media = detailedRelease.media ?: release.media
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadAllTracksDetails() {
        val currentRelease = selectedRelease ?: return

        viewModelScope.launch {
            currentRelease.media?.let { mediaList ->
                val updatedMediaList = mutableListOf<Media>()

                for (mediaItem in mediaList) {
                    val updatedTracks = mutableListOf<Track>()

                    for (track in mediaItem.tracks.orEmpty()) {
                        // Llamamos a la función individual para cada pista
                        val updatedTrack = loadSingleTrackDetail(track)
                        updatedTracks.add(updatedTrack)
                    }

                    updatedMediaList.add(mediaItem.copy(tracks = updatedTracks))
                }

                // Actualizamos de forma segura el release completo con las pistas enriquecidas
                withContext(Dispatchers.Main) {
                    selectedRelease = selectedRelease?.copy(media = updatedMediaList)
                }
            }
        }
    }

    suspend fun loadSingleTrackDetail(track: Track): Track {
        val recordingId = track.recording?.id
        val trackKey = track.id ?: track.title ?: return track

        if (recordingId != null) {
            return try {
                delay(1200.milliseconds) // Rate limit de MusicBrainz
                val detailedRecording = musicBrainzService.getRecordingDetails(recordingId)

                // Pasamos el álbum actual (selectedRelease) como contexto padre
                val enrichedRecording = track.recording?.let { existingRec ->
                    mergeWithDetailedRecording(existingRec, detailedRecording, parentRelease = selectedRelease)
                } ?: mergeWithDetailedRecording(track.recording ?: Recording(null, null, null, null, null, null, null, null, null), detailedRecording, parentRelease = selectedRelease)

                trackSyncStatuses[trackKey] = TrackSyncStatus.SYNCED
                track.copy(recording = enrichedRecording)
            } catch (e: Exception) {
                trackSyncStatuses[trackKey] = TrackSyncStatus.ERROR
                track
            }
        }
        return track
    }

    fun reloadSingleTrack(track: Track) {
        val trackKey = track.id ?: track.title ?: return
        val currentRelease = selectedRelease ?: return

        viewModelScope.launch {
            // Marcamos esta pista como "cargando"
            trackLoadingStates[trackKey] = true

            // Llamamos a la función individual que ya teníamos creada
            val updatedTrack = loadSingleTrackDetail(track)

            // Actualizamos la lista de medios reemplazando únicamente la pista modificada
            val updatedMediaList = currentRelease.media?.map { mediaItem ->
                val newTracks = mediaItem.tracks?.map { t ->
                    if ((t.id != null && t.id == updatedTrack.id) || (t.title == updatedTrack.title)) {
                        updatedTrack
                    } else {
                        t
                    }
                }
                mediaItem.copy(tracks = newTracks)
            }

            // Actualizamos el estado del release con la pista ya recargada
            withContext(Dispatchers.Main) {
                selectedRelease = currentRelease.copy(media = updatedMediaList)
                // Quitamos el estado de carga
                trackLoadingStates[trackKey] = false
            }
        }
    }

    fun openRecordingModalDirectly(recording: Recording, mbid: String?, parentRelease: Release? = null) {
        previousRelease = parentRelease // Guardamos el álbum del que venimos (si aplica)
        selectedRecording = recording
        selectedRelease = null         // Ocultamos temporalmente el release para mostrar la canción
        selectedImageTitle = recording.title
        selectedImageMbid = mbid
        isModalOpen = true
    }

    // Función para regresar al álbum anterior desde el modal de la canción
    fun backToReleaseModal() {
        val rel = previousRelease ?: return
        selectedRelease = rel
        selectedRecording = null
        selectedImageTitle = rel.title
        selectedImageMbid = rel.id
        previousRelease = null // Limpiamos el rastro
    }

    fun closeImageModal() {
        isModalOpen = false
        selectedRecording = null
        selectedRelease = null
        selectedImageMbid = null
        selectedImageTitle = null
    }

    private fun mergeWithDetailedRecording(baseRecording: Recording, detailedRecording: Recording): Recording {
        // Obtenemos el release original y el detallado (si existe)
        val oldRelease = baseRecording.releases?.firstOrNull()
        val newRelease = detailedRecording.releases?.firstOrNull()

        // Fusionamos el release: mantenemos los medios (pista/disco) del viejo y la fecha/status del nuevo
        val mergedRelease = oldRelease?.copy(
            date = newRelease?.date ?: oldRelease.date,
            country = newRelease?.country ?: oldRelease.country,
            status = newRelease?.status ?: oldRelease.status,
            media = oldRelease.media ?: newRelease?.media
        )

        // Fusionamos la grabación combinando campos nuevos sin perder los básicos anteriores
        return baseRecording.copy(
            genres = detailedRecording.genres ?: baseRecording.genres,
            tagList = detailedRecording.tagList ?: baseRecording.tagList,
            releases = mergedRelease?.let { listOf(it) } ?: baseRecording.releases,
            length = detailedRecording.length ?: baseRecording.length,
            artistCredit = detailedRecording.artistCredit ?: baseRecording.artistCredit
        )
    }

    private fun mergeWithDetailedRecording(
        baseRecording: Recording,
        detailedRecording: Recording,
        parentRelease: Release? = null
    ): Recording {
        // Obtenemos el release original de la grabación o el detallado
        val oldRelease = baseRecording.releases?.firstOrNull()
        val newRelease = detailedRecording.releases?.firstOrNull()

        // PRIORIDAD DE DATOS:
        // 1. Si tenemos un parentRelease (contexto del álbum actual), lo usamos como base principal.
        // 2. Si no, usamos el del detalle o el viejo.
        val targetRelease = parentRelease ?: oldRelease ?: newRelease

        val mergedRelease = targetRelease?.copy(
            title = targetRelease.title ?: newRelease?.title ?: oldRelease?.title,
            date = parentRelease?.date ?: newRelease?.date ?: oldRelease?.date,
            country = parentRelease?.country ?: newRelease?.country ?: oldRelease?.country,
            status = parentRelease?.status ?: newRelease?.status ?: oldRelease?.status,
            // ¡Crucial! Si el parentRelease tiene los medios (pistas/discos), se los pasamos a la grabación
            // para que la UI pueda calcular el número de pista y disco correctamente.
            media = parentRelease?.media ?: newRelease?.media ?: oldRelease?.media
        )

        // Fusionamos la grabación combinando géneros, tags y asegurando el release enriquecido
        return baseRecording.copy(
            genres = detailedRecording.genres ?: baseRecording.genres,
            tagList = detailedRecording.tagList ?: baseRecording.tagList,
            releases = mergedRelease?.let { listOf(it) } ?: detailedRecording.releases ?: baseRecording.releases,
            length = detailedRecording.length ?: baseRecording.length,
            artistCredit = detailedRecording.artistCredit ?: baseRecording.artistCredit
        )
    }
}