package com.tuapp.eleuthera.ui.settings

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuapp.eleuthera.domain.model.AppFont
import com.tuapp.eleuthera.domain.model.AudioFormat
import com.tuapp.eleuthera.domain.model.ComponentStyle
import com.tuapp.eleuthera.domain.model.ThemeConfig
import com.tuapp.eleuthera.ui.login.CookieLoginActivity
import com.tuapp.eleuthera.ui.musicbrainz.MusicBrainzActivity
import com.tuapp.eleuthera.ui.theme.AppButton
import com.tuapp.eleuthera.ui.theme.AppCard

private enum class SettingsPage(val title: String, val icon: ImageVector) {
    APPEARANCE("Apariencia", Icons.Default.Palette),
    PROGRESS("Barra de progreso", Icons.Default.LinearScale),
    LIBRARY("Biblioteca", Icons.Default.LibraryMusic),
    DOWNLOADS("Descargas", Icons.Default.Download),
    ACCOUNT("Cuenta de YouTube", Icons.Default.AccountCircle),
    ABOUT("Acerca de", Icons.Default.Info),
}

/**
 * Ajustes: un menú de categorías; cada una abre su página. Cada fila muestra cómo está
 * configurada ahora, para no tener que entrar a mirar.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    vm: ThemeViewModel,
    cookiesVm: CookiesViewModel = hiltViewModel(),
    downloadsVm: DownloadSettingsViewModel = hiltViewModel(),
    libraryVm: LibrarySettingsViewModel = hiltViewModel(),
    skinVm: ProgressSkinViewModel = hiltViewModel(),
    aboutVm: AboutViewModel = hiltViewModel(),
) {
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    val back = { page = null }

    when (page) {
        SettingsPage.APPEARANCE -> AppearanceSettingsPage(vm, back, modifier)
        SettingsPage.PROGRESS -> ProgressSkinPage(skinVm, back, modifier)
        SettingsPage.LIBRARY -> LibrarySettingsPage(libraryVm, back, modifier)
        SettingsPage.DOWNLOADS -> DownloadSettingsPage(downloadsVm, back, modifier)
        SettingsPage.ACCOUNT -> AccountSettingsPage(cookiesVm, back, modifier)
        SettingsPage.ABOUT -> AboutPage(aboutVm, back, modifier)
        null -> SettingsMenu(
            modifier = modifier,
            subtitles = rememberSubtitles(vm, cookiesVm, downloadsVm, libraryVm, skinVm, aboutVm),
            onOpen = { page = it },
        )
    }
}

@Composable
private fun SettingsMenu(
    modifier: Modifier,
    subtitles: Map<SettingsPage, String>,
    onOpen: (SettingsPage) -> Unit,
) {
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
        MusicBrainzSection()
    }
}

/** Resumen de cada categoría para el menú. */
@Composable
private fun rememberSubtitles(
    vm: ThemeViewModel,
    cookiesVm: CookiesViewModel,
    downloadsVm: DownloadSettingsViewModel,
    libraryVm: LibrarySettingsViewModel,
    skinVm: ProgressSkinViewModel,
    aboutVm: AboutViewModel,
): Map<SettingsPage, String> {
    val config by vm.config.collectAsStateWithLifecycle()
    val cookies by cookiesVm.present.collectAsStateWithLifecycle()
    val format by downloadsVm.format.collectAsStateWithLifecycle()
    val folders by libraryVm.folders.collectAsStateWithLifecycle()
    val skins by skinVm.skins.collectAsStateWithLifecycle()
    val activeSkin by skinVm.activeId.collectAsStateWithLifecycle()
    val about by aboutVm.state.collectAsStateWithLifecycle()

    return mapOf(
        SettingsPage.APPEARANCE to "${presetName(config)} · ${config.font.label} · ${config.style.label}",
        SettingsPage.PROGRESS to (skins.firstOrNull { it.id == activeSkin }?.name ?: "Por defecto"),
        SettingsPage.LIBRARY to when (folders.size) {
            0 -> "Música/Eleuthera"
            1 -> "Música/Eleuthera + 1 carpeta"
            else -> "Música/Eleuthera + ${folders.size} carpetas"
        },
        SettingsPage.DOWNLOADS to format.label,
        SettingsPage.ACCOUNT to if (cookies) "Sesión guardada" else "Sin cookies",
        SettingsPage.ABOUT to "Versión ${about.appVersion}",
    )
}

/*MusicBrainz*/
@Composable
private fun MusicBrainzSection() {
    val context = LocalContext.current

    SectionTitle("Music Brainz")
    Column(
        Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppButton(
            onClick = { context.startActivity(Intent(context, MusicBrainzActivity::class.java)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Buscar en MusicBrainz")
        }
    }
}