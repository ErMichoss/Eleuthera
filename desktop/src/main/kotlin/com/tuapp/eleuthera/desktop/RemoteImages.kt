package com.tuapp.eleuthera.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.tuapp.eleuthera.ui.platform.decodeImage
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/**
 * Miniaturas de YouTube en escritorio (en Android lo hace coil). Se descargan una vez y las
 * últimas se quedan en memoria, para que la lista de resultados no parpadee al hacer scroll.
 */
private object RemoteImages {
    private val http: HttpClient by lazy {
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .proxy(ProxySelector.getDefault())
            .build()
    }

    private val memory = object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>) = size > 200
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(4)

    fun cached(url: String): ImageBitmap? = synchronized(memory) { memory[url] }

    suspend fun load(url: String): ImageBitmap? = cached(url) ?: withContext(io) {
        runCatching {
            val request = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(15)).GET().build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofByteArray())
            if (response.statusCode() in 200..299) decodeImage(response.body()) else null
        }.getOrNull()
    }?.also { synchronized(memory) { memory[url] = it } }
}

/** Marca de "todavía cargando" para [desktopRemoteImage]. */
private object Loading

/**
 * Para LocalRemoteImage: la imagen de [url] recortada, con un hueco gris mientras carga. Si no
 * se puede cargar queda transparente, para que se vea lo que haya debajo (p. ej. un icono).
 */
val desktopRemoteImage: @Composable (String, Modifier) -> Unit = { url, modifier ->
    val state by produceState<Any?>(RemoteImages.cached(url) ?: Loading, url) {
        if (value === Loading) value = if (url.isBlank()) null else RemoteImages.load(url)
    }
    val loading = state === Loading
    Box(if (loading) modifier.background(MaterialTheme.colorScheme.surfaceVariant) else modifier) {
        (state as? ImageBitmap)?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}
