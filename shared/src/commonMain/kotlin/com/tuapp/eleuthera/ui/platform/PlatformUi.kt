package com.tuapp.eleuthera.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import com.tuapp.eleuthera.platform.PlatformUri

/**
 * Lo poco que las pantallas comunes necesitan de cada plataforma.
 *
 * Las dos CompositionLocal las pone la raíz de cada app (MainActivity en Android, Main.kt en
 * escritorio); si faltan, no hacen nada en vez de fallar.
 */

/** Botón o gesto "atrás" de Android. En escritorio no existe y no hace nada. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

/** Imagen codificada (jpg, png...) a ImageBitmap, o null si no se puede leer. */
expect fun decodeImage(bytes: ByteArray): ImageBitmap?

/** Aviso breve: Toast en Android, barra inferior (snackbar) en escritorio. */
val LocalToast = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Carátula reducida de un archivo de música, o null si no tiene. */
val LocalArtLoader = staticCompositionLocalOf<suspend (PlatformUri) -> ImageBitmap?> { { null } }