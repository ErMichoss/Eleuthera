package com.tuapp.eleuthera.di

import android.content.Context
import androidx.work.WorkManager
import com.tuapp.eleuthera.data.db.AppDatabase
import com.tuapp.eleuthera.data.db.DownloadDao
import com.tuapp.eleuthera.data.db.FolderTrackDao
import com.tuapp.eleuthera.data.db.PlaylistDao
import com.tuapp.eleuthera.data.db.buildAppDatabase
import com.tuapp.eleuthera.data.lrclib.LrclibService
import com.tuapp.eleuthera.data.settings.PreferenceStores
import java.io.File
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

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
    fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideDownloadDao(db: AppDatabase): DownloadDao = db.downloadDao()

    @Provides
    fun provideFolderTrackDao(db: AppDatabase): FolderTrackDao = db.folderTrackDao()
}