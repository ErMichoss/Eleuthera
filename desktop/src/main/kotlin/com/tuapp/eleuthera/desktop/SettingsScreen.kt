package com.tuapp.eleuthera.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tuapp.eleuthera.platform.DesktopPaths
import com.tuapp.eleuthera.platform.FileUri
import com.tuapp.eleuthera.platform.Tools
import com.tuapp.eleuthera.ui.platform.LocalToast
import com.tuapp.eleuthera.ui.settings.AppearanceSettingsPage
import com.tuapp.eleuthera.ui.settings.DownloadSettingsPage
import com.tuapp.eleuthera.ui.settings.ProgressSkinSettingsPage
import com.tuapp.eleuthera.ui.settings.OptionChip
import com.tuapp.eleuthera.ui.settings.ScrollRow
import com.tuapp.eleuthera.ui.settings.SectionTitle
import com.tuapp.eleuthera.ui.settings.SettingsHint
import com.tuapp.eleuthera.ui.settings.SettingsPageScaffold
import com.tuapp.eleuthera.ui.settings.presetName
import java.awt.Desktop
import kotlinx.coroutines.launch

/**
 * Categorías de Ajustes en escritorio. Apariencia y Descargas son las mismas páginas que en
 * Android; Biblioteca y Acerca de son propias (carpetas del disco y herramientas instaladas).
 * Barra de progreso es la misma que en Android. Cuenta de YouTube importa un cookies.txt.
 */
private enum class SettingsPage(val title: String, val icon: ImageVector) {
    APPEARANCE("Apariencia", Icons.Default.Palette),
    PROGRESS("Barra de progreso", Icons.Default.LinearScale),
    LIBRARY("Biblioteca", Icons.Default.LibraryMusic),
    DOWNLOADS("Descargas", Icons.Default.Download),
    ACCOUNT("Cuenta de YouTube", Icons.Default.AccountCircle),
    ABOUT("Acerca de", Icons.Default.Info),
}

private val appVersion: String get() = AppGraph.appVersion

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    val back = { page = null }
    // En una ventana ancha, las páginas no se estiran de lado a lado.
    val pageModifier = modifier.widthIn(max = 760.dp)

    when (page) {
        SettingsPage.APPEARANCE -> AppearanceSettingsPage(AppGraph.themeUi, back, pageModifier)
        SettingsPage.PROGRESS -> ProgressSkinSettingsPage(
            vm = AppGraph.skins,
            pickImage = { onPicked -> FolderPicker.chooseImage()?.let { onPicked(FileUri(it)) } },
            onBack = back,
            modifier = pageModifier,
        )
        SettingsPage.LIBRARY -> LibrarySettingsPage(back, pageModifier)
        SettingsPage.DOWNLOADS -> DownloadSettingsPage(AppGraph.downloadSettingsUi, back, pageModifier)
        SettingsPage.ACCOUNT -> AccountPage(back, pageModifier)
        SettingsPage.ABOUT -> AboutPage(back, pageModifier)
        null -> SettingsMenu(pageModifier) { page = it }
    }
}

@Composable
private fun SettingsMenu(modifier: Modifier, onOpen: (SettingsPage) -> Unit) {
    val config by AppGraph.themeUi.config.collectAsState()
    val format by AppGraph.downloadSettingsUi.format.collectAsState()
    val folders by AppGraph.librarySettings.extraFolders.collectAsState(emptyList())
    val skins by AppGraph.skins.skins.collectAsState()
    val activeSkin by AppGraph.skins.activeId.collectAsState()
    val cookies by AppGraph.cookies.present.collectAsState()
    val subtitles = mapOf(
        SettingsPage.APPEARANCE to "${presetName(config)} · ${config.font.label} · ${config.style.label}",
        SettingsPage.PROGRESS to (skins.firstOrNull { it.id == activeSkin }?.name ?: "Por defecto"),
        SettingsPage.LIBRARY to when (folders.size) {
            0 -> "Música/Eleuthera"
            1 -> "Música/Eleuthera + 1 carpeta"
            else -> "Música/Eleuthera + ${folders.size} carpetas"
        },
        SettingsPage.DOWNLOADS to format.label,
        SettingsPage.ACCOUNT to if (cookies) "Sesión guardada" else "Sin cookies",
        SettingsPage.ABOUT to "Versión $appVersion",
    )

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {
        Text(
            "Ajustes",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        SettingsPage.entries.forEach { item ->
            ListItem(
                modifier = Modifier.clickable { onOpen(item) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(item.icon, contentDescription = null) },
                headlineContent = { Text(item.title) },
                supportingContent = { subtitles[item]?.let { Text(it, maxLines = 1) } },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                },
            )
        }
    }
}

// ---------- Biblioteca: carpetas de música ----------

@Composable
private fun LibrarySettingsPage(onBack: () -> Unit, modifier: Modifier) {
    val settings = AppGraph.librarySettings
    val extra by settings.extraFolders.collectAsState(emptyList())
    val pendingTags by AppGraph.library.pendingTags.collectAsState()
    val scope = rememberCoroutineScope()

    SettingsPageScaffold("Biblioteca", onBack, modifier) {
        SettingsHint(
            "La biblioteca muestra las canciones de estas carpetas y sus subcarpetas: " +
                "mp3, m4a, webm, opus, ogg, flac y wav."
        )
        if (pendingTags > 0) {
            Text(
                "Leyendo etiquetas: faltan $pendingTags canciones.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            FolderRow(DesktopPaths.musicDir.path, "Descargas", onRemove = null)
            extra.forEach { dir ->
                FolderRow(dir.path, dir.name.ifEmpty { dir.path }) { scope.launch { settings.remove(dir) } }
            }
            Row {
                TextButton(onClick = {
                    FolderPicker.choose()?.let { dir -> scope.launch { settings.add(dir) } }
                }) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Añadir carpeta")
                }
                TextButton(onClick = AppGraph.library::refresh) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Buscar canciones nuevas")
                }
            }
        }
    }
}

@Composable
private fun FolderRow(path: String, label: String, onRemove: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                path, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) { Icon(Icons.Default.Close, contentDescription = "Quitar carpeta") }
        } else {
            Spacer(Modifier.height(48.dp))
        }
    }
}

// ---------- Cuenta de YouTube: cookies ----------

@Composable
private fun AccountPage(onBack: () -> Unit, modifier: Modifier) {
    val store = AppGraph.cookies
    val present by store.present.collectAsState()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }

    // Navegadores que yt-dlp sabe leer, según el sistema. Firefox primero: es el que siempre funciona.
    val browsers = remember {
        buildList {
            add("firefox" to "Firefox")
            add("chrome" to "Chrome")
            add("edge" to "Edge")
            add("brave" to "Brave")
            add("opera" to "Opera")
            add("vivaldi" to "Vivaldi")
            if (DesktopPaths.isMac) add("safari" to "Safari")
            if (!DesktopPaths.isWindows && !DesktopPaths.isMac) add("chromium" to "Chromium")
        }
    }
    var browser by remember { mutableStateOf(browsers.first().first) }
    var importing by remember { mutableStateOf(false) }

    SettingsPageScaffold("Cuenta de YouTube", onBack, modifier) {
        Text(
            if (present) "Cookies cargadas: se usan en búsquedas y descargas."
            else "Sin cookies: las peticiones van como usuario anónimo.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        SectionTitle("Desde tu navegador")
        SettingsHint("Elige el navegador donde tienes YouTube abierto con tu cuenta y pulsa «Traer sesión».")
        ScrollRow {
            browsers.forEach { (id, label) ->
                OptionChip(selected = browser == id, onClick = { browser = id }, label = label)
            }
        }
        TextButton(
            enabled = !importing,
            modifier = Modifier.padding(horizontal = 8.dp),
            onClick = {
                importing = true
                message = null
                scope.launch {
                    message = store.importFromBrowser(browser, AppGraph.ytDlp::exportBrowserCookies).fold(
                        onSuccess = { "Sesión traída: $it cookies de YouTube." },
                        onFailure = { it.message ?: "No se pudo leer el navegador." },
                    )
                    importing = false
                }
            },
        ) {
            Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (importing) "Leyendo el navegador…" else "Traer sesión")
        }
        if (DesktopPaths.isWindows) {
            SettingsHint(
                "En Windows, Chrome, Edge y Brave cifran sus cookies y a veces no se pueden leer; " +
                    "además hay que cerrarlos del todo antes. Con Firefox funciona siempre."
            )
        }
        message?.let {
            Text(
                it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        SectionTitle("Desde un archivo")
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    val file = FolderPicker.chooseFile("Elige el archivo de cookies", setOf("txt", "json"))
                    if (file != null) scope.launch {
                        message = store.import(file).fold(
                            onSuccess = { "Importadas $it cookies." },
                            onFailure = { it.message ?: "No se pudo importar el archivo." },
                        )
                    }
                }) {
                    Icon(Icons.Default.FileOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Importar archivo (.txt o .json)")
                }
                if (present) TextButton(onClick = { store.clear(); message = "Cookies eliminadas." }) { Text("Quitar") }
            }
        }
        SectionTitle("Cómo conseguir el archivo")
        SettingsHint(
            "1. En tu navegador, abre youtube.com con la sesión iniciada.\n" +
                "2. Instala una extensión que exporte cookies, por ejemplo «Get cookies.txt LOCALLY» " +
                "(Chrome, Edge, Firefox) o «Cookie-Editor».\n" +
                "3. Exporta las cookies de youtube.com e impórtalas aquí."
        )
        SettingsHint(
            "Las cookies sirven cuando YouTube pide confirmar que no eres un bot y para vídeos " +
                "con restricción de edad. Usa una cuenta secundaria (por ejemplo, en un perfil aparte " +
                "de Firefox): con muchas descargas, YouTube puede bloquearla. Solo se guardan las " +
                "cookies de YouTube y Google, en la carpeta de datos de la app."
        )
    }
}

// ---------- Acerca de: versión y herramientas ----------

@Composable
private fun AboutPage(onBack: () -> Unit, modifier: Modifier) {
    val tools by Tools.state.collectAsState()
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    // Versiones de las herramientas; se vuelven a leer cuando cambian (p. ej. tras actualizar).
    var ytDlpVersion by remember { mutableStateOf<String?>(null) }
    var ffmpegVersion by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var checkingApp by remember { mutableStateOf(false) }
    LaunchedEffect(tools.ytDlp, tools.ffmpeg, refresh) {
        ytDlpVersion = Tools.versionOf(tools.ytDlp ?: Tools.ytDlp())
        ffmpegVersion = Tools.versionOf(tools.ffmpeg, flag = "-version")
            ?.removePrefix("ffmpeg version ")?.substringBefore(' ')
    }

    SettingsPageScaffold("Acerca de", onBack, modifier) {
        SectionTitle("Eleuthera")
        InfoRow("Versión", appVersion)
        InfoRow("Datos de la app", DesktopPaths.dataDir.path)
        TextButton(
            enabled = !checkingApp,
            modifier = Modifier.padding(horizontal = 8.dp),
            onClick = {
                checkingApp = true
                scope.launch {
                    AppGraph.updates.checkNow()
                        .onSuccess { if (it == null) toast("Tienes la última versión.") }
                        .onFailure { toast("No se pudo comprobar. Revisa la conexión.") }
                    checkingApp = false
                }
            },
        ) {
            Icon(Icons.Default.SystemUpdate, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (checkingApp) "Comprobando…" else "Buscar actualizaciones")
        }

        SectionTitle("Herramientas")
        SettingsHint(
            "Eleuthera las descarga y actualiza sola en su carpeta. Si tienes alguna instalada " +
                "en el sistema, se usa esa."
        )
        InfoRow("yt-dlp", ytDlpVersion ?: if (tools.ytDlp == null) "No instalado" else "…")
        InfoRow("ffmpeg", ffmpegVersion ?: if (tools.ffmpeg == null) "No instalado" else "…")
        InfoRow("Deno", if (tools.deno != null) "Instalado" else "No instalado (opcional)")
        Row(Modifier.padding(horizontal = 8.dp)) {
            TextButton(
                enabled = tools.working == null,
                onClick = {
                    scope.launch {
                        toast(Tools.updateYtDlp())
                        refresh++
                    }
                },
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Actualizar yt-dlp")
            }
            TextButton(onClick = {
                runCatching { Desktop.getDesktop().open(DesktopPaths.dataDir) }
                    .onFailure { toast("No se pudo abrir la carpeta") }
            }) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Abrir carpeta de datos")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(160.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
