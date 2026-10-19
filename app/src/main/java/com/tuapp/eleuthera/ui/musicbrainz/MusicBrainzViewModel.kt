package com.tuapp.eleuthera.ui.musicbrainz

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.eleuthera.BuildConfig
import com.tuapp.eleuthera.data.musicbrainz.MusicBrainzService

/**
 * MusicBrainz en Android: toda la lógica está en [MusicBrainzController] (:shared, común con
 * escritorio). Aquí solo se abre el navegador para iniciar sesión.
 */
class MusicBrainzViewModel : ViewModel() {

    val controller = MusicBrainzController(
        viewModelScope,
        MusicBrainzService(
            clientId = BuildConfig.MUSICBRAINZ_CLIENT_ID,
            clientSecret = BuildConfig.MUSICBRAINZ_CLIENT_SECRET,
            appVersion = BuildConfig.VERSION_NAME,
        ),
    )

    fun startOAuthLogin(context: Context) {
        context.startActivity(Intent(Intent.ACTION_VIEW, controller.authorizeUrl().toUri()))
    }

    /** La llama la Activity al volver del navegador con el código. */
    fun exchangeCodeForToken(code: String) = controller.exchangeCodeForToken(code)
}
