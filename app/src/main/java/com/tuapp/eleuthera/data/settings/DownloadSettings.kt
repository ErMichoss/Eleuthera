package com.tuapp.eleuthera.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tuapp.eleuthera.domain.model.AudioFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.downloadStore by preferencesDataStore(name = "downloads")

/** Preferencias de descarga. */
@Singleton
class DownloadSettings @Inject constructor(@ApplicationContext private val ctx: Context) {

    private val formatKey = stringPreferencesKey("format")

    val format: Flow<AudioFormat> = ctx.downloadStore.data.map { p ->
        p[formatKey]?.let { runCatching { AudioFormat.valueOf(it) }.getOrNull() } ?: AudioFormat.MP3
    }

    suspend fun setFormat(format: AudioFormat) {
        ctx.downloadStore.edit { it[formatKey] = format.name }
    }
}
