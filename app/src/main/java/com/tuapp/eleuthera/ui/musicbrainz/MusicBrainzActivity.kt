package com.tuapp.eleuthera.ui.musicbrainz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme

class MusicBrainzActivity : ComponentActivity() {
    private val viewModel: MusicBrainzViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Aplica el tema propio de tu aplicación aquí si lo deseas
            MaterialTheme {
                MusicBrainzScreen(viewModel = viewModel)
            }
        }
    }
}