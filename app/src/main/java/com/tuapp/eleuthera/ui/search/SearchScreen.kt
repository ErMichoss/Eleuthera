package com.tuapp.eleuthera.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.tuapp.eleuthera.ui.library.TrackActions
import com.tuapp.eleuthera.ui.playlists.PlaylistsController
import com.tuapp.eleuthera.ui.importer.ImportPanel

/**
 * Buscar en Android: la pantalla común con el panel de importar playlists debajo del buscador.
 * [actions] y [playlists] permiten descargar y reproducir / mandar a la cola / a una playlist.
 */
@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    actions: TrackActions = TrackActions(),
    playlists: PlaylistsController? = null,
    vm: SearchViewModel = hiltViewModel(),
) {
    SearchContent(vm.controller, modifier, actions = actions, playlists = playlists, header = { ImportPanel() })
}