package com.tuapp.eleuthera

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.tuapp.eleuthera.data.youtube.YtDlpEngine
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class EleutheraApp : Application(), Configuration.Provider {

    @Inject lateinit var engine: YtDlpEngine
    @Inject lateinit var workerFactory: HiltWorkerFactory

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Permite que los Workers reciban dependencias de Hilt. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Prepara y actualiza el motor en segundo plano (la primera vez descomprime Python y FFmpeg).
        appScope.launch { runCatching { engine.ensureReady() } }
    }
}
