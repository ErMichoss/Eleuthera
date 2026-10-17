package com.tuapp.eleuthera.desktop

import com.tuapp.eleuthera.data.db.AppDatabase
import com.tuapp.eleuthera.data.db.buildAppDatabase
import com.tuapp.eleuthera.data.settings.DownloadSettings
import com.tuapp.eleuthera.data.settings.PreferenceStores
import com.tuapp.eleuthera.data.settings.ThemeRepository
import com.tuapp.eleuthera.platform.DesktopPaths

/**
 * Las piezas de la app de escritorio, creadas una sola vez. Hace lo que Hilt hace en Android
 * (AppModule): aquí son pocas, así que se montan a mano sin librería de inyección.
 */
object AppGraph {
    val stores by lazy { PreferenceStores(DesktopPaths.dataDir) }
    val themeRepository by lazy { ThemeRepository(stores) }
    val downloadSettings by lazy { DownloadSettings(stores) }
    val database: AppDatabase by lazy { buildAppDatabase() }
}