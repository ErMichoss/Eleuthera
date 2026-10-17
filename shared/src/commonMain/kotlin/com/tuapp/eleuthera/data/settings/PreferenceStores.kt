package com.tuapp.eleuthera.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File
import okio.Path.Companion.toPath

/**
 * Abre los archivos de ajustes (DataStore) de la app. [baseDir] es la carpeta privada de la
 * app: filesDir en Android y DesktopPaths.dataDir en escritorio.
 *
 * Los archivos quedan en baseDir/datastore/<nombre>.preferences_pb, exactamente donde los
 * dejaba `preferencesDataStore(name)` en Android, así que los ajustes de antes se conservan.
 */
class PreferenceStores(private val baseDir: File) {

    fun get(name: String): DataStore<Preferences> =
        open(File(baseDir, "datastore/$name.preferences_pb"))

    private companion object {
        // DataStore no permite dos instancias sobre el mismo archivo: se abre una sola vez
        // por archivo y se reutiliza (p. ej. el widget y la app comparten la del tema).
        private val opened = HashMap<String, DataStore<Preferences>>()

        fun open(file: File): DataStore<Preferences> = synchronized(opened) {
            opened.getOrPut(file.absolutePath) {
                PreferenceDataStoreFactory.createWithPath(produceFile = { file.absolutePath.toPath() })
            }
        }
    }
}