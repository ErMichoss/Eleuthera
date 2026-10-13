package com.tuapp.eleuthera.ui.spotify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

class SpotifyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Contenedor principal que aplica el tema de la aplicación
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TextoEjemploScreen(mensaje = "¡Hola! Este es un texto de ejemplo con Jetpack Compose.", viewModel = viewModel())
                }
            }
        }
    }
}

// Vista ejecutable y reutilizable
@Composable
fun TextoEjemploScreen(mensaje: String, modifier: Modifier = Modifier, viewModel: SpotifyViewModel) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Mostramos el valor actual del contador del ViewModel
        Text(
            text = "Valor: ${viewModel.contador}",
            fontSize = 32.sp
        )

        // Botón para disparar la acción
        Button(onClick = { viewModel.incrementarContador() }) {
            Text("Incrementar")
        }
    }
}