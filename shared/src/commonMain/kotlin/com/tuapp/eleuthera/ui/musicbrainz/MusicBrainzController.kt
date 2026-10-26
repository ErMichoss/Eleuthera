package com.tuapp.eleuthera.ui.musicbrainz

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tuapp.eleuthera.data.musicbrainz.Media
import com.tuapp.eleuthera.data.musicbrainz.MusicBrainzRepository
import com.tuapp.eleuthera.data.musicbrainz.Recording
import com.tuapp.eleuthera.data.musicbrainz.Release
import com.tuapp.eleuthera.data.musicbrainz.Track
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class SearchType { RECORDING, RELEASE }

class MusicBrainzController(
    private val scope: CoroutineScope,
    private val repository: MusicBrainzRepository, // <-- Ahora inyectamos el repositorio
) {
    enum class TrackSyncStatus { PENDING, SYNCED, ERROR }

    var searchType by mutableStateOf(SearchType.RECORDING)
    var titleInput by mutableStateOf("")
    var artistInput by mutableStateOf("")
    var limitInput by mutableStateOf("25")
    var showTrackNumber by mutableStateOf(true)

    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    var notice by mutableStateOf<String?>(null)

    var loggedIn by mutableStateOf(false)
        private set

    var recordingsResult by mutableStateOf<List<Recording>>(emptyList())
    var releasesResult by mutableStateOf<List<Release>>(emptyList())

    var isModalOpen by mutableStateOf(false)
    var selectedRecording by mutableStateOf<Recording?>(null)
    var selectedRelease by mutableStateOf<Release?>(null)
    var selectedImageTitle by mutableStateOf<String?>(null)
    var selectedImageMbid by mutableStateOf<String?>(null)

    val trackSyncStatuses = mutableStateMapOf<String, TrackSyncStatus>()
    val trackLoadingStates = mutableStateMapOf<String, Boolean>()
    var previousRelease by mutableStateOf<Release?>(null)

    val canLogIn: Boolean get() = repository.canLogIn

    fun authorizeUrl(): String = repository.authorizeUrl()

    fun exchangeCodeForToken(code: String) {
        scope.launch {
            isLoading = true
            val success = repository.exchangeCodeForToken(code)
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
                    recordingsResult = repository.searchRecordings(
                        title = titleInput,
                        artist = artistInput,
                        limit = limit
                    ).recordings ?: emptyList()
                    releasesResult = emptyList()
                } else {
                    releasesResult = repository.searchReleases(
                        title = titleInput,
                        artist = artistInput,
                        limit = limit
                    ).releases ?: emptyList()
                    recordingsResult = emptyList()
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Error de red al conectar con MusicBrainz."
            } finally {
                isLoading = false
            }
        }
    }

    fun openRecordingModal(recording: Recording, mbid: String?) {
        selectedRecording = recording
        selectedRelease = null
        selectedImageTitle = recording.title
        selectedImageMbid = mbid
        isModalOpen = true

        val id = recording.id ?: return
        scope.launch {
            try {
                delay(1200.milliseconds)
                val detailed = repository.getRecordingDetails(id)
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
                val detailed = repository.getReleaseDetails(id)
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
                delay(1200.milliseconds)
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
            delay(1200.milliseconds)
            val detailed = repository.getRecordingDetails(recordingId) // <-- Llamada al repositorio
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
        selectedRelease = null
        selectedImageTitle = recording.title
        selectedImageMbid = mbid
        isModalOpen = true
    }

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

    private fun mergeBasic(base: Recording, detailed: Recording): Recording {
        val oldRelease = base.releases?.firstOrNull()
        val newRelease = detailed.releases?.firstOrNull()
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