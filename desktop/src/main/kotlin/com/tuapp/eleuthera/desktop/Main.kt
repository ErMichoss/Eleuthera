package com.tuapp.eleuthera.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tuapp.eleuthera.data.db.PlaylistEntity
import com.tuapp.eleuthera.domain.model.AppFont
import com.tuapp.eleuthera.domain.model.ThemeConfig
import com.tuapp.eleuthera.platform.DesktopPaths
import com.tuapp.eleuthera.ui.theme.AppButton
import com.tuapp.eleuthera.ui.theme.AppCard
import com.tuapp.eleuthera.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * Punto de entrada de la app de escritorio. De momento es una pantalla de prueba de la
 * fase 2: lee el tema guardado y las playlists de la base de datos común.
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Eleuthera",
        state = rememberWindowState(width = 1100.dp, height = 720.dp),
    ) {
        val theme by AppGraph.themeRepository.config.collectAsState(ThemeConfig())
        AppTheme(theme) { TestScreen(theme) }
    }
}

@androidx.compose.runtime.Composable
private fun TestScreen(theme: ThemeConfig) {
    val scope = rememberCoroutineScope()
    val dao = AppGraph.database.playlistDao()
    val playlists by dao.observePlaylists().collectAsState(emptyList())

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Eleuthera", style = MaterialTheme.typography.displayMedium)
            Text(
                "Datos en ${DesktopPaths.dataDir}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Prueba de ajustes: cambia la fuente y se guarda; al reabrir la app se mantiene.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Fuente: ${theme.font.label}")
                AppButton(onClick = {
                    val next = AppFont.entries[(theme.font.ordinal + 1) % AppFont.entries.size]
                    scope.launch { AppGraph.themeRepository.save(theme.copy(font = next)) }
                }) { Text("Cambiar fuente") }
            }

            // Prueba de base de datos: crea playlists vacías y las lista.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Playlists: ${playlists.size}")
                AppButton(onClick = {
                    scope.launch { dao.insert(PlaylistEntity(name = "Prueba ${playlists.size + 1}")) }
                }) { Text("Crear playlist de prueba") }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(playlists, key = { it.id }) { p ->
                    AppCard { Text(p.name, Modifier.padding(16.dp)) }
                }
            }
        }
    }
}