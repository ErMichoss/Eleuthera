package com.tuapp.eleuthera.ui.musicbrainz

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

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
    var selectedImageMbid by mutableStateOf<String?>(null)
    var selectedImageTitle by mutableStateOf<String?>(null)
    var isModalOpen by mutableStateOf(false)

    // Mapa para registrar qué IDs de elementos fallaron al cargar su carátula
    val failedImages = mutableStateMapOf<String, Boolean>()

    fun markImageAsFailed(id: String) {
        failedImages[id] = true
    }

    private val api: MusicBrainzApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://musicbrainz.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MusicBrainzApiService::class.java)
    }

    fun search() {
        if (titleInput.isBlank()) {
            errorMessage = "Por favor ingresa un título para la búsqueda."
            return
        }
        errorMessage = null
        isLoading = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (searchType == SearchType.RECORDING) {
                    val query = buildString {
                        append("recording:\"").append(escapeLucene(titleInput)).append("\" AND video:false")
                        if (artistInput.isNotBlank()) {
                            append(" AND artistname:\"").append(escapeLucene(artistInput)).append("\"")
                        }
                    }
                    val response = api.searchRecordings(query, limitInput.toIntOrNull() ?: 25)
                    recordingsResult = response.recordings ?: emptyList()
                    releasesResult = emptyList()
                } else {
                    val query = buildString {
                        append("release:\"").append(escapeLucene(titleInput)).append("\"")
                        if (artistInput.isNotBlank()) {
                            append(" AND artistname:\"").append(escapeLucene(artistInput)).append("\"")
                        }
                    }
                    val response = api.searchReleases(query, limitInput.toIntOrNull() ?: 25)
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

    fun openImageModal(title: String, mbid: String?) {
        selectedImageTitle = title
        selectedImageMbid = mbid
        isModalOpen = true
    }

    fun closeImageModal() {
        isModalOpen = false
        selectedImageMbid = null
    }
}