package com.tuapp.eleuthera.ui.musicbrainz

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.tuapp.eleuthera.ui.platform.LocalRemoteImage
import com.tuapp.eleuthera.ui.platform.LocalToast
import com.tuapp.eleuthera.ui.settings.ThemeViewModel
import com.tuapp.eleuthera.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

private val coilImage: @Composable (String, Modifier) -> Unit = { url, mod ->
    AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = mod)
}

@AndroidEntryPoint
class MusicBrainzActivity : ComponentActivity() {
    private val viewModel: MusicBrainzViewModel by viewModels()
    private val themeViewModel: ThemeViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleOAuthRedirect(intent)

        setContent {
            val themeConfig by themeViewModel.config.collectAsState()
            AppTheme(config = themeConfig) {
                // Es otra Activity: necesita sus propios avisos e imágenes de internet.
                CompositionLocalProvider(
                    LocalToast provides { msg -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show() },
                    LocalRemoteImage provides coilImage,
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("MusicBrainz") },
                                navigationIcon = {
                                    IconButton(onClick = ::finish) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                                    }
                                },
                            )
                        },
                    ) { padding ->
                        MusicBrainzContent(
                            viewModel = viewModel.controller,
                            modifier = Modifier.padding(padding),
                            onLogin = { viewModel.startOAuthLogin(this) },
                        )
                    }
                }
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
