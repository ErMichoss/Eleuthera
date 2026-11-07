package com.tuapp.eleuthera.data.metadata

import android.net.Uri
import android.util.Log
import com.tuapp.eleuthera.ui.lyrics.LyricsStorage
import com.tuapp.eleuthera.ui.lyrics.LyricsTrack
import com.tuapp.eleuthera.ui.lyrics.LocalLyricsResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class AndroidLyricsStorage @Inject constructor(
    private val embeddedLyricsReader: EmbeddedLyricsReader,
    private val subtitleWriter: LyricsSubtitleWriter,
) : LyricsStorage {

    /** Prioriza el LRC/SRT para que las correcciones del usuario prevalezcan sobre las incrustadas. */
    override suspend fun read(track: LyricsTrack): LocalLyricsResult {
        val uri = Uri.parse(track.uri)
        var sidecarError: Throwable? = null
        val sidecar = try {
            subtitleWriter.readLocal(uri)?.takeIf(String::isNotBlank)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            sidecarError = e
            Log.w(TAG, "No se pudieron leer LRC/SRT de '${track.title}' ($uri).", e)
            null
        }
        if (sidecar != null) return LocalLyricsResult(text = sidecar)

        var embeddedError: Throwable? = null
        val embedded = try {
            embeddedLyricsReader.read(uri)?.takeIf(String::isNotBlank)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            embeddedError = e
            Log.w(TAG, "No se pudieron leer letras incrustadas de '${track.title}' ($uri).", e)
            null
        }
        return LocalLyricsResult(
            text = embedded,
            embeddedReadError = embeddedError,
            sidecarReadError = sidecarError,
        )
    }

    /** Delega el guardado al adaptador Android, que elige SAF o almacenamiento privado. */
    override suspend fun save(
        track: LyricsTrack,
        lyrics: String,
        isStillCurrent: () -> Boolean,
        replaceExisting: Boolean,
    ): String = subtitleWriter.save(
        uri = Uri.parse(track.uri),
        lyrics = lyrics,
        isStillCurrent = isStillCurrent,
        replaceExisting = replaceExisting,
    )

    private companion object {
        const val TAG = "AndroidLyricsStorage"
    }
}
