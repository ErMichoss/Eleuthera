package com.tuapp.eleuthera.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.eleuthera.data.settings.DownloadSettings
import com.tuapp.eleuthera.domain.model.AudioFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DownloadSettingsViewModel @Inject constructor(
    private val settings: DownloadSettings,
) : ViewModel(), DownloadSettingsUi {

    override val format: StateFlow<AudioFormat> =
        settings.format.stateIn(viewModelScope, SharingStarted.Eagerly, AudioFormat.MP3)

    override fun setFormat(format: AudioFormat) {
        viewModelScope.launch { settings.setFormat(format) }
    }
}