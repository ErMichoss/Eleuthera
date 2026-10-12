package com.tuapp.eleuthera.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tuapp.eleuthera.domain.model.AppFont
import com.tuapp.eleuthera.domain.model.ComponentStyle
import com.tuapp.eleuthera.domain.model.ThemeConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeStore by preferencesDataStore(name = "theme")

/** Persiste la configuración de tema y la expone como flujo reactivo. */
@Singleton
class ThemeRepository @Inject constructor(@ApplicationContext private val ctx: Context) {

    private object Keys {
        val primary = intPreferencesKey("primary")
        val secondary = intPreferencesKey("secondary")
        val background = intPreferencesKey("background")
        val accent = intPreferencesKey("accent")
        val font = stringPreferencesKey("font")
        val radius = intPreferencesKey("radius")
        val style = stringPreferencesKey("style")
    }

    val config: Flow<ThemeConfig> = ctx.themeStore.data.map { p ->
        val d = ThemeConfig()
        ThemeConfig(
            primary = p[Keys.primary] ?: d.primary,
            secondary = p[Keys.secondary] ?: d.secondary,
            background = p[Keys.background] ?: d.background,
            accent = p[Keys.accent] ?: d.accent,
            // runCatching: un valor guardado por una versión antigua no debe romper la app.
            font = p[Keys.font]?.let { runCatching { AppFont.valueOf(it) }.getOrNull() } ?: d.font,
            cornerRadiusDp = p[Keys.radius] ?: d.cornerRadiusDp,
            style = p[Keys.style]?.let { runCatching { ComponentStyle.valueOf(it) }.getOrNull() }
                ?: d.style,
        )
    }

    suspend fun save(c: ThemeConfig) {
        ctx.themeStore.edit { p ->
            p[Keys.primary] = c.primary
            p[Keys.secondary] = c.secondary
            p[Keys.background] = c.background
            p[Keys.accent] = c.accent
            p[Keys.font] = c.font.name
            p[Keys.radius] = c.cornerRadiusDp
            p[Keys.style] = c.style.name
        }
    }
}
