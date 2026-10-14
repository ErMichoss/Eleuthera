package com.tuapp.eleuthera.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.eleuthera.data.update.UpdateChecker
import com.tuapp.eleuthera.data.youtube.YtDlpEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AboutState(
    val appVersion: String = "",
    val engineVersion: String? = null,
    val updating: Boolean = false,
    val message: String? = null,
    val checkingApp: Boolean = false,
    val appMessage: String? = null,
)

@HiltViewModel
class AboutViewModel @Inject constructor(
    @ApplicationContext ctx: Context,
    private val engine: YtDlpEngine,
    private val updates: UpdateChecker,
) : ViewModel() {

    private val _state = MutableStateFlow(
        AboutState(
            appVersion = runCatching {
                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
            }.getOrNull().orEmpty(),
        )
    )
    val state: StateFlow<AboutState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(engineVersion = engine.binaryVersion())
        }
    }

    /** Busca una versión nueva de la app; si la hay, aparece el aviso de actualización. */
    fun checkAppUpdate() {
        if (_state.value.checkingApp) return
        _state.value = _state.value.copy(checkingApp = true, appMessage = null)
        viewModelScope.launch {
            val message = updates.checkNow().fold(
                onSuccess = { if (it == null) "Tienes la última versión." else null },
                onFailure = { "No se pudo comprobar. Revisa la conexión." },
            )
            _state.value = _state.value.copy(checkingApp = false, appMessage = message)
        }
    }

    fun updateEngine() {
        if (_state.value.updating) return
        _state.value = _state.value.copy(updating = true, message = null)
        viewModelScope.launch {
            val message = try {
                if (engine.updateNow()) "Motor actualizado." else "Ya tenías la última versión."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "No se pudo actualizar. Comprueba la conexión."
            }
            _state.value = _state.value.copy(
                updating = false,
                message = message,
                engineVersion = engine.binaryVersion(),
            )
        }
    }
}