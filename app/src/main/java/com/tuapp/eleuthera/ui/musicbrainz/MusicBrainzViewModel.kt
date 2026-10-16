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
import com.tuapp.eleuthera.data.musicbrainz.MusicBrainzService
import com.tuapp.eleuthera.data.musicbrainz.Recording
import com.tuapp.eleuthera.data.musicbrainz.Release
import com.tuapp.eleuthera.domain.model.ThemeConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

    fun startOAuthLogin(context: Context) {
        val clientId = BuildConfig.MUSICBRAINZ_CLIENT_ID // El Client ID que obtienes al registrar tu app en MusicBrainz
        val redirectUri = "com.tuapp.eleuthera://oauth"

        val authUrl = Uri.parse("https://musicbrainz.org/oauth2/authorize").buildUpon()
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

    fun openReleaseModal(release: Release) {
        selectedRelease = release
        selectedRecording = null
        selectedImageTitle = release.title
        selectedImageMbid = release.id
        isModalOpen = true
    }

    fun closeImageModal() {
        isModalOpen = false
        selectedRecording = null
        selectedRelease = null
        selectedImageMbid = null
        selectedImageTitle = null
    }
}