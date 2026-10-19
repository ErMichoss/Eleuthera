package com.tuapp.eleuthera.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.tuapp.eleuthera.ui.importer.ImportPanel

/** Buscar en Android: la pantalla común con el panel de importar playlists debajo del buscador. */
@Composable
fun SearchScreen(modifier: Modifier = Modifier, vm: SearchViewModel = hiltViewModel()) {
    SearchContent(vm.controller, modifier, header = { ImportPanel() })
}