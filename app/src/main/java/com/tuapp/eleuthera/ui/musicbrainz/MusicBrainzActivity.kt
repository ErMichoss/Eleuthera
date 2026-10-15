package com.tuapp.eleuthera.ui.musicbrainz

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme

class MusicBrainzActivity : ComponentActivity() {
    private val viewModel: MusicBrainzViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleOAuthRedirect(intent)

        setContent {
            //TODO: Aplicar el tema de app
            MaterialTheme {
                MusicBrainzScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Por si la Activity ya estaba en memoria cuando vuelve del navegador
        handleOAuthRedirect(intent)
    }

    private fun handleOAuthRedirect(intent: Intent?) {
        val uri = intent?.data
        if (uri != null && uri.scheme == "com.tuapp.eleuthera" && uri.host == "oauth") {
            val authCode = uri.getQueryParameter("code")
            if (!authCode.isNullOrBlank()) {
                viewModel.exchangeCodeForToken(authCode)
            }
        }
    }
}