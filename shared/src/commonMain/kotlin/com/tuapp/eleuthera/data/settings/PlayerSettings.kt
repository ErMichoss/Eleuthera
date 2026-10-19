package com.tuapp.eleuthera.data.settings

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Ajustes del reproductor que se recuerdan entre sesiones (de momento, el volumen de escritorio). */
@Singleton
class PlayerSettings @Inject constructor(stores: PreferenceStores) {

    private val store = stores.get("player")
    private val volumeKey = floatPreferencesKey("volume")

    val volume: Flow<Float> = store.data.map { it[volumeKey] ?: 1f }

    suspend fun setVolume(value: Float) {
        store.edit { it[volumeKey] = value.coerceIn(0f, 1f) }
    }
}