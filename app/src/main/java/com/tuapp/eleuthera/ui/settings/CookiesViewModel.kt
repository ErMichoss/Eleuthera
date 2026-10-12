package com.tuapp.eleuthera.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.eleuthera.data.settings.CookieStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CookiesViewModel @Inject constructor(private val store: CookieStore) : ViewModel() {

    val present: StateFlow<Boolean> = store.present

    private val _message = MutableStateFlow<String?>(null)
    /** Resultado de la última importación, para mostrarlo bajo los botones. */
    val message: StateFlow<String?> = _message.asStateFlow()

    fun import(uri: Uri) {
        viewModelScope.launch {
            _message.value = store.import(uri).fold(
                onSuccess = { "Importadas $it cookies." },
                onFailure = { it.message ?: "No se pudo importar el archivo." },
            )
        }
    }

    fun clear() {
        store.clear()
        _message.value = "Cookies eliminadas."
    }
}
