package com.tuapp.eleuthera.di

import android.content.Context
import androidx.work.WorkManager
import com.tuapp.eleuthera.data.db.AppDatabase
import com.tuapp.eleuthera.data.db.DownloadDao
import com.tuapp.eleuthera.data.db.FolderTrackDao
import com.tuapp.eleuthera.data.db.PlaylistDao
import com.tuapp.eleuthera.data.db.buildAppDatabase
import com.tuapp.eleuthera.data.lrclib.LrclibService
import com.tuapp.eleuthera.data.lrclib.LyricsRepository
import com.tuapp.eleuthera.data.metadata.AndroidLyricsStorage
import com.tuapp.eleuthera.data.settings.PreferenceStores
import com.tuapp.eleuthera.ui.lyrics.LyricsController
import com.tuapp.eleuthera.ui.lyrics.LyricsLogLevel
import java.io.File
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    fun provideWorkManager(@ApplicationContext ctx: Context): WorkManager =
        WorkManager.getInstance(ctx)

    /** La base de datos está en :shared; aquí solo se crea con el contexto de Android. */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase = buildAppDatabase(ctx)

    /** Ajustes comunes (tema, descargas...), en filesDir/datastore como antes. */
    @Provides
    @Singleton
    fun providePreferenceStores(@ApplicationContext ctx: Context): PreferenceStores =
        PreferenceStores(ctx.filesDir)

    @Provides
    @Singleton
    fun provideLrclibService(@ApplicationContext ctx: Context): LrclibService =
        LrclibService(File(ctx.filesDir, "lrclib/lyrics-cache.json"))

    @Provides
    @Singleton
    fun provideLyricsRepository(service: LrclibService): LyricsRepository =
        LyricsRepository(service)

    @Provides
    @Singleton
    fun provideLyricsController(
        repository: LyricsRepository,
        storage: AndroidLyricsStorage,
    ): LyricsController = LyricsController(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        repository = repository,
        storage = storage,
    ) { level, message, error ->
        when (level) {
            LyricsLogLevel.DEBUG -> android.util.Log.d("LyricsController", message, error)
            LyricsLogLevel.INFO -> android.util.Log.i("LyricsController", message, error)
            LyricsLogLevel.WARNING -> android.util.Log.w("LyricsController", message, error)
            LyricsLogLevel.ERROR -> android.util.Log.e("LyricsController", message, error)
        }
    }

    @Provides
    fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideDownloadDao(db: AppDatabase): DownloadDao = db.downloadDao()

    @Provides
    fun provideFolderTrackDao(db: AppDatabase): FolderTrackDao = db.folderTrackDao()
}