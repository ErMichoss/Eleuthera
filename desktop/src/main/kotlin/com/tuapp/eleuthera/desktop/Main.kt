package com.tuapp.eleuthera.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tuapp.eleuthera.domain.model.AppFont
import com.tuapp.eleuthera.domain.model.ThemeConfig
import com.tuapp.eleuthera.ui.theme.AppButton
import com.tuapp.eleuthera.ui.theme.AppTheme

/** Punto de entrada de la app de escritorio. De momento, solo comprueba que el tema común funciona. */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Eleuthera",
        state = rememberWindowState(width = 1100.dp, height = 720.dp),
    ) {
        AppTheme(ThemeConfig(font = AppFont.MONTSERRAT)) {
            Surface(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Eleuthera", style = MaterialTheme.typography.displayMedium)
                    Text(
                        "Versión de escritorio en construcción",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AppButton(onClick = {}) { Text("Botón con el estilo de la app") }
                }
            }
        }
    }
}