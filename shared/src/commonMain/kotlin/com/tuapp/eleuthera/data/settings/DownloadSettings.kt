package com.tuapp.eleuthera.data.settings

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tuapp.eleuthera.domain.model.AudioFormat
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Preferencias de descarga. Común a Android y escritorio. */
@Singleton
class DownloadSettings @Inject constructor(stores: PreferenceStores) {

    private val store = stores.get("downloads")

    private val formatKey = stringPreferencesKey("format")

    val format: Flow<AudioFormat> = store.data.map { p ->
        p[formatKey]?.let { runCatching { AudioFormat.valueOf(it) }.getOrNull() } ?: AudioFormat.MP3
    }

    suspend fun setFormat(format: AudioFormat) {
        store.edit { it[formatKey] = format.name }
    }
}