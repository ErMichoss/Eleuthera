package com.tuapp.eleuthera.ui.spotify

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class SpotifyViewModel : ViewModel() {

    // Estado privado para que solo el ViewModel pueda modificarlo
    var contador by mutableStateOf(0)
        private set

    // Función que incrementa el contador
    fun incrementarContador() {
        contador++
    }
}