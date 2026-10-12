package com.tuapp.eleuthera.ui.login

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuapp.eleuthera.data.settings.CookieStore
import com.tuapp.eleuthera.ui.settings.ThemeViewModel
import com.tuapp.eleuthera.ui.theme.AppButton
import com.tuapp.eleuthera.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Inicio de sesión en YouTube dentro de la app. Al pulsar "Guardar sesión" se copian las
 * cookies del navegador interno al cookies.txt que usa yt-dlp, y se borran del navegador
 * para que la sesión no se siga usando (y rotando) desde aquí.
 */
@AndroidEntryPoint
class CookieLoginActivity : ComponentActivity() {

    @Inject lateinit var cookieStore: CookieStore

    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val cookies = CookieManager.getInstance().apply { setAcceptCookie(true) }

        setContent {
            val themeVm: ThemeViewModel = hiltViewModel()
            val theme by themeVm.config.collectAsStateWithLifecycle()
            var progress by remember { mutableIntStateOf(0) }
            var canGoBack by remember { mutableStateOf(false) }

            BackHandler(enabled = canGoBack) { webView?.goBack() }

            AppTheme(theme) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = ::finish) {
                                Icon(Icons.Default.Close, contentDescription = "Cancelar")
                            }
                            Text(
                                "Inicia sesión y pulsa Guardar",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            AppButton(onClick = ::save, modifier = Modifier.padding(end = 8.dp)) {
                                Text("Guardar sesión")
                            }
                        }
                        if (progress in 1..99) {
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                WebView(context).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    // Sin la marca "; wv" Google bloquea menos el inicio de sesión.
                                    settings.userAgentString =
                                        settings.userAgentString.replace("; wv", "")
                                    cookies.setAcceptThirdPartyCookies(this, true)
                                    webViewClient = object : WebViewClient() {
                                        override fun doUpdateVisitedHistory(
                                            view: WebView, url: String?, isReload: Boolean,
                                        ) {
                                            canGoBack = view.canGoBack()
                                        }
                                    }
                                    webChromeClient = object : android.webkit.WebChromeClient() {
                                        override fun onProgressChanged(view: WebView, p: Int) {
                                            progress = p
                                        }
                                    }
                                    loadUrl(LOGIN_URL)
                                    webView = this
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    private fun save() {
        val manager = CookieManager.getInstance()
        manager.flush()
        val header = manager.getCookie("https://www.youtube.com").orEmpty()
        val result = cookieStore.saveFromHeader(header)
        result.onSuccess { count ->
            // La sesión queda solo en el cookies.txt; el navegador interno la olvida.
            manager.removeAllCookies(null)
            Toast.makeText(this, "Sesión guardada ($count cookies)", Toast.LENGTH_LONG).show()
            finish()
        }.onFailure {
            Toast.makeText(this, it.message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    private companion object {
        const val LOGIN_URL =
            "https://accounts.google.com/ServiceLogin?service=youtube" +
                "&continue=https%3A%2F%2Fwww.youtube.com%2F"
    }
}
