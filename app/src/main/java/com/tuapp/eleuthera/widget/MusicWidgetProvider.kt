package com.tuapp.eleuthera.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.tuapp.eleuthera.MainActivity
import com.tuapp.eleuthera.R
import com.tuapp.eleuthera.data.settings.ThemeRepository
import com.tuapp.eleuthera.domain.model.ThemeConfig
import com.tuapp.eleuthera.playback.PlaybackService
import com.tuapp.eleuthera.playback.PlayerState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MusicWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action in listOf(ACTION_PLAY_PAUSE, ACTION_NEXT, ACTION_PREV, ACTION_SHUFFLE, ACTION_REPEAT)) {
            val pendingResult = goAsync()
            val appContext = context.applicationContext
            executeMediaAction(appContext) { player ->
                try {
                    when (action) {
                        ACTION_PLAY_PAUSE -> {
                            if (player.isPlaying) player.pause() else player.play()
                        }
                        ACTION_NEXT -> {
                            if (player.hasNextMediaItem()) player.seekToNext()
                        }
                        ACTION_PREV -> {
                            if (player.hasPreviousMediaItem()) player.seekToPrevious()
                        }
                        ACTION_SHUFFLE -> {
                            player.shuffleModeEnabled = !player.shuffleModeEnabled
                        }
                        ACTION_REPEAT -> {
                            player.repeatMode = when (player.repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun executeMediaAction(appContext: Context, action: (Player) -> Unit) {
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                action(controller)
                controller.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.tuapp.eleuthera.widget.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.tuapp.eleuthera.widget.ACTION_NEXT"
        const val ACTION_PREV = "com.tuapp.eleuthera.widget.ACTION_PREV"
        const val ACTION_SHUFFLE = "com.tuapp.eleuthera.widget.ACTION_SHUFFLE"
        const val ACTION_REPEAT = "com.tuapp.eleuthera.widget.ACTION_REPEAT"

        fun updateWidget(context: Context, state: PlayerState) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(
                ComponentName(context, MusicWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return

            val themeConfig = runCatching {
                runBlocking { ThemeRepository(context).config.first() }
            }.getOrDefault(ThemeConfig())

            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_music_player)

                // Background con el color del tema y radio de bordes del usuario
                val bgBitmap = createRoundedBackground(
                    context,
                    themeConfig.background,
                    themeConfig.cornerRadiusDp
                )
                views.setImageViewBitmap(R.id.widget_bg, bgBitmap)

                views.setTextViewText(
                    R.id.widget_title,
                    state.title.ifBlank { "Eleuthera" }
                )
                views.setTextViewText(
                    R.id.widget_artist,
                    state.artist.ifBlank { "Selecciona una canción" }
                )

                // Botón Reproducir / Pausa
                val playPauseIcon = if (state.isPlaying) {
                    R.drawable.ic_widget_pause
                } else {
                    R.drawable.ic_widget_play
                }
                views.setImageViewResource(R.id.widget_btn_play_pause, playPauseIcon)

                // Botón Aleatorio (Shuffle)
                val activeColor = themeConfig.primary
                val inactiveColor = 0x80FFFFFF.toInt()

                val shuffleColor = if (state.shuffle) activeColor else inactiveColor
                views.setInt(R.id.widget_btn_shuffle, "setColorFilter", shuffleColor)

                // Botón Repetir (Repeat)
                val repeatColor = if (state.repeatMode != Player.REPEAT_MODE_OFF) activeColor else inactiveColor
                views.setInt(R.id.widget_btn_repeat, "setColorFilter", repeatColor)

                val repeatIcon = if (state.repeatMode == Player.REPEAT_MODE_ONE) {
                    R.drawable.ic_widget_repeat_one
                } else {
                    R.drawable.ic_widget_repeat
                }
                views.setImageViewResource(R.id.widget_btn_repeat, repeatIcon)

                // Portada
                if (state.artwork != null && state.artwork.isNotEmpty()) {
                    val bitmap = BitmapFactory.decodeByteArray(state.artwork, 0, state.artwork.size)
                    if (bitmap != null) {
                        views.setImageViewBitmap(R.id.widget_artwork, bitmap)
                    } else {
                        views.setImageViewResource(R.id.widget_artwork, R.drawable.ic_widget_music_note)
                    }
                } else {
                    views.setImageViewResource(R.id.widget_artwork, R.drawable.ic_widget_music_note)
                }

                setupPendingIntents(context, views)
                appWidgetManager.updateAppWidget(id, views)
            }
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_music_player)
            val themeConfig = runCatching {
                runBlocking { ThemeRepository(context).config.first() }
            }.getOrDefault(ThemeConfig())

            val bgBitmap = createRoundedBackground(
                context,
                themeConfig.background,
                themeConfig.cornerRadiusDp
            )
            views.setImageViewBitmap(R.id.widget_bg, bgBitmap)

            setupPendingIntents(context, views)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun createRoundedBackground(
            context: Context,
            color: Int,
            radiusDp: Int
        ): Bitmap {
            val density = context.resources.displayMetrics.density
            val widthPx = (300 * density).toInt().coerceAtLeast(100)
            val heightPx = (90 * density).toInt().coerceAtLeast(100)
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.FILL
            }
            val rect = RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat())
            val radiusPx = radiusDp * density
            canvas.drawRoundRect(rect, radiusPx, radiusPx, paint)
            return bitmap
        }

        private fun setupPendingIntents(context: Context, views: RemoteViews) {
            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_text_container, openAppPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_artwork, openAppPendingIntent)

            val playPauseIntent = Intent(context, MusicWidgetProvider::class.java).apply {
                action = ACTION_PLAY_PAUSE
            }
            val playPausePendingIntent = PendingIntent.getBroadcast(
                context, 1, playPauseIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_btn_play_pause, playPausePendingIntent)

            val nextIntent = Intent(context, MusicWidgetProvider::class.java).apply {
                action = ACTION_NEXT
            }
            val nextPendingIntent = PendingIntent.getBroadcast(
                context, 2, nextIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_btn_next, nextPendingIntent)

            val prevIntent = Intent(context, MusicWidgetProvider::class.java).apply {
                action = ACTION_PREV
            }
            val prevPendingIntent = PendingIntent.getBroadcast(
                context, 3, prevIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_btn_prev, prevPendingIntent)

            val shuffleIntent = Intent(context, MusicWidgetProvider::class.java).apply {
                action = ACTION_SHUFFLE
            }
            val shufflePendingIntent = PendingIntent.getBroadcast(
                context, 4, shuffleIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_btn_shuffle, shufflePendingIntent)

            val repeatIntent = Intent(context, MusicWidgetProvider::class.java).apply {
                action = ACTION_REPEAT
            }
            val repeatPendingIntent = PendingIntent.getBroadcast(
                context, 5, repeatIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_btn_repeat, repeatPendingIntent)
        }
    }
}
