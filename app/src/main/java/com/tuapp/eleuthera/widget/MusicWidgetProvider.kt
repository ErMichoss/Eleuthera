package com.tuapp.eleuthera.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.util.LruCache
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.asAndroidBitmap
import com.tuapp.eleuthera.MainActivity
import com.tuapp.eleuthera.R
import com.tuapp.eleuthera.data.settings.LoadedSkin
import com.tuapp.eleuthera.data.settings.ProgressSkinRepository
import com.tuapp.eleuthera.data.settings.ThemeRepository
import com.tuapp.eleuthera.domain.model.ThemeConfig
import com.tuapp.eleuthera.domain.model.TrackMode
import com.tuapp.eleuthera.playback.PlayerController
import com.tuapp.eleuthera.playback.PlayerState
import com.tuapp.eleuthera.playback.RepeatMode
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Widget del reproductor de música.
 * Soporta redimensionado dinámico, temas visuales y pieles de barra de progreso.
 */
@AndroidEntryPoint
class MusicWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var player: PlayerController

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action in listOf(ACTION_PLAY_PAUSE, ACTION_NEXT, ACTION_PREV, ACTION_SHUFFLE, ACTION_REPEAT)) {
            val pendingResult = goAsync()
            val finished = AtomicBoolean(false)
            val finish = {
                if (finished.compareAndSet(false, true)) pendingResult.finish()
            }
            player.runWhenReady(
                action = {
                    try {
                        when (action) {
                            ACTION_PLAY_PAUSE -> player.toggle()
                            ACTION_NEXT -> player.next()
                            ACTION_PREV -> player.previous()
                            ACTION_SHUFFLE -> player.toggleShuffle()
                            ACTION_REPEAT -> player.cycleRepeat()
                        }
                    } finally {
                        finish()
                    }
                },
                onUnavailable = finish,
            )
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.tuapp.eleuthera.widget.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.tuapp.eleuthera.widget.ACTION_NEXT"
        const val ACTION_PREV = "com.tuapp.eleuthera.widget.ACTION_PREV"
        const val ACTION_SHUFFLE = "com.tuapp.eleuthera.widget.ACTION_SHUFFLE"
        const val ACTION_REPEAT = "com.tuapp.eleuthera.widget.ACTION_REPEAT"

        private data class BackgroundKey(
            val color: Int,
            val radiusDp: Int,
            val widthPx: Int,
            val heightPx: Int,
        )

        private val backgroundCache = LruCache<BackgroundKey, Bitmap>(2)
        private var cachedArtworkMediaId: String? = null
        private var cachedArtworkBytes: ByteArray? = null
        private var cachedArtworkBitmap: Bitmap? = null

        /** Redibuja el widget. [themeConfig] y [loadedSkin] los pasa PlayerController. */
        fun updateWidget(
            context: Context,
            state: PlayerState,
            themeConfig: ThemeConfig,
            loadedSkin: LoadedSkin? = null
        ) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(
                ComponentName(context, MusicWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return

            val artwork = cachedArtwork(state)

            val activeSkin = loadedSkin ?: runCatching {
                runBlocking { ProgressSkinRepository(context).active.first() }
            }.getOrNull()

            for (id in ids) {
                val options = appWidgetManager.getAppWidgetOptions(id)
                val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250).coerceAtLeast(120)
                val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 90).coerceAtLeast(60)

                val views = RemoteViews(context.packageName, R.layout.widget_music_player)
                configureWidgetSize(views, widthDp)

                // Fondo dinámico adaptado al tamaño del widget
                val bgBitmap = createRoundedBackground(
                    context,
                    themeConfig.background,
                    themeConfig.cornerRadiusDp,
                    widthDp,
                    heightDp
                )
                views.setImageViewBitmap(R.id.widget_bg, bgBitmap)

                views.setTextViewText(
                    R.id.widget_title,
                    state.title.ifBlank { "Eleuthera" }
                )
                views.setTextColor(R.id.widget_title, themeConfig.primary)

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

                // Botones Shuffle / Repeat
                val activeColor = themeConfig.primary
                val inactiveColor = 0x80FFFFFF.toInt()

                val shuffleColor = if (state.shuffle) activeColor else inactiveColor
                views.setInt(R.id.widget_btn_shuffle, "setColorFilter", shuffleColor)

                val repeatColor = if (state.repeat != RepeatMode.OFF) activeColor else inactiveColor
                views.setInt(R.id.widget_btn_repeat, "setColorFilter", repeatColor)

                val repeatIcon = if (state.repeat == RepeatMode.ONE) {
                    R.drawable.ic_widget_repeat_one
                } else {
                    R.drawable.ic_widget_repeat
                }
                views.setImageViewResource(R.id.widget_btn_repeat, repeatIcon)

                // Portada
                if (artwork != null) {
                    views.setImageViewBitmap(R.id.widget_artwork, artwork)
                } else {
                    views.setImageViewResource(R.id.widget_artwork, R.drawable.ic_widget_music_note)
                }

                // Tiempos y Barra de Progreso adaptada al ancho
                views.setTextViewText(R.id.widget_time_current, formatTime(state.positionMs))
                views.setTextViewText(R.id.widget_time_total, formatTime(state.durationMs))

                val progressBitmap = createProgressBarBitmap(
                    context,
                    state.positionMs,
                    state.durationMs,
                    themeConfig,
                    activeSkin,
                    widthDp
                )
                views.setImageViewBitmap(R.id.widget_progress_bar, progressBitmap)

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

            val activeSkin = runCatching {
                runBlocking { ProgressSkinRepository(context).active.first() }
            }.getOrNull()

            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250).coerceAtLeast(120)
            val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 90).coerceAtLeast(60)
            configureWidgetSize(views, widthDp)

            val bgBitmap = createRoundedBackground(
                context,
                themeConfig.background,
                themeConfig.cornerRadiusDp,
                widthDp,
                heightDp
            )
            views.setImageViewBitmap(R.id.widget_bg, bgBitmap)
            views.setTextColor(R.id.widget_title, themeConfig.primary)

            val progressBitmap = createProgressBarBitmap(context, 0, 0, themeConfig, activeSkin, widthDp)
            views.setImageViewBitmap(R.id.widget_progress_bar, progressBitmap)
            views.setTextViewText(R.id.widget_time_current, "0:00")
            views.setTextViewText(R.id.widget_time_total, "0:00")

            setupPendingIntents(context, views)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun formatTime(ms: Long): String {
            if (ms <= 0) return "0:00"
            val totalSeconds = ms / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }

        private fun cachedArtwork(state: PlayerState): Bitmap? {
            val bytes = state.artwork?.takeIf { it.isNotEmpty() }
            if (cachedArtworkMediaId != state.mediaId || cachedArtworkBytes !== bytes) {
                cachedArtworkMediaId = state.mediaId
                cachedArtworkBytes = bytes
                cachedArtworkBitmap = bytes?.let { decodeSmall(it, 256) }
            }
            return cachedArtworkBitmap
        }

        private fun configureWidgetSize(views: RemoteViews, widthDp: Int) {
            val compactButtonVisibility = if (widthDp < 300) View.GONE else View.VISIBLE
            views.setViewVisibility(R.id.widget_btn_shuffle, compactButtonVisibility)
            views.setViewVisibility(R.id.widget_btn_repeat, compactButtonVisibility)
        }

        private fun createProgressBarBitmap(
            context: Context,
            positionMs: Long,
            durationMs: Long,
            themeConfig: ThemeConfig,
            loadedSkin: LoadedSkin?,
            widgetWidthDp: Int
        ): Bitmap {
            val density = context.resources.displayMetrics.density
            val widthPx = ((widgetWidthDp - 60) * density).toInt().coerceAtLeast(100)
            val heightPx = (30 * density).toInt().coerceAtLeast(30)

            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
            val splitX = fraction * widthPx

            val barHeight = (10 * density).coerceAtLeast(8f)
            val barTop = (heightPx - barHeight) / 2f
            val barBottom = barTop + barHeight
            val radius = barHeight / 2f

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            val skin = loadedSkin?.skin
            val mode = skin?.mode ?: TrackMode.TILE

            val playedBmp = loadedSkin?.played?.asAndroidBitmap()
                ?: skin?.played?.takeIf { File(it).exists() }?.let { BitmapFactory.decodeFile(it) }
            val remainingBmp = loadedSkin?.remaining?.asAndroidBitmap()
                ?: skin?.remaining?.takeIf { File(it).exists() }?.let { BitmapFactory.decodeFile(it) }
            val thumbBmp = loadedSkin?.thumb?.asAndroidBitmap()
                ?: skin?.thumb?.takeIf { File(it).exists() }?.let { BitmapFactory.decodeFile(it) }

            if (mode == TrackMode.REVEAL && playedBmp != null) {
                // Modo REVEAL: una sola imagen a todo lo ancho
                val paintDesat = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    alpha = 90
                    colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
                }
                canvas.save()
                canvas.clipRect(splitX, 0f, widthPx.toFloat(), heightPx.toFloat())
                val dest = RectF(0f, barTop, widthPx.toFloat(), barBottom)
                canvas.drawBitmap(playedBmp, null, dest, paintDesat)
                canvas.restore()

                if (splitX > 0) {
                    canvas.save()
                    canvas.clipRect(0f, 0f, splitX, heightPx.toFloat())
                    canvas.drawBitmap(playedBmp, null, dest, paint)
                    canvas.restore()
                }
            } else {
                // Modo TILE / STRETCH
                // 1. Parte pendiente (Remaining) - Imagen tiene prioridad absoluta sobre color
                if (remainingBmp != null) {
                    canvas.save()
                    canvas.clipRect(splitX, 0f, widthPx.toFloat(), heightPx.toFloat())
                    drawSkinBar(canvas, remainingBmp, 0f, barTop, widthPx.toFloat(), barBottom, mode)
                    canvas.restore()
                } else {
                    paint.color = themeConfig.secondary.takeIf { it != 0 } ?: 0x33FFFFFF.toInt()
                    val rectRem = RectF(0f, barTop, widthPx.toFloat(), barBottom)
                    canvas.drawRoundRect(rectRem, radius, radius, paint)
                }

                // 2. Parte reproducida (Played) - Imagen tiene prioridad absoluta sobre color
                if (splitX > 0) {
                    if (playedBmp != null) {
                        canvas.save()
                        canvas.clipRect(0f, 0f, splitX, heightPx.toFloat())
                        drawSkinBar(canvas, playedBmp, 0f, barTop, widthPx.toFloat(), barBottom, mode)
                        canvas.restore()
                    } else {
                        paint.color = themeConfig.primary
                        canvas.save()
                        canvas.clipRect(0f, 0f, splitX, heightPx.toFloat())
                        val rectPlayed = RectF(0f, barTop, widthPx.toFloat(), barBottom)
                        canvas.drawRoundRect(rectPlayed, radius, radius, paint)
                        canvas.restore()
                    }
                }
            }

            // 3. Indicador (Thumb) - Imagen tiene prioridad sobre el círculo por defecto
            if (thumbBmp != null) {
                val thumbSize = heightPx.toFloat()
                val thumbLeft = (splitX - thumbSize / 2f).coerceIn(0f, widthPx - thumbSize)
                val thumbRect = RectF(thumbLeft, 0f, thumbLeft + thumbSize, heightPx.toFloat())
                canvas.drawBitmap(thumbBmp, null, thumbRect, paint)
            } else {
                paint.color = themeConfig.primary
                val thumbRadius = (12 * density)
                val cx = splitX.coerceIn(thumbRadius, widthPx - thumbRadius)
                canvas.drawCircle(cx, heightPx / 2f, thumbRadius, paint)
            }

            return bitmap
        }

        private fun drawSkinBar(
            canvas: Canvas,
            src: Bitmap,
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            mode: TrackMode
        ) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            when (mode) {
                TrackMode.STRETCH, TrackMode.REVEAL -> {
                    val dest = RectF(left, top, right, bottom)
                    canvas.drawBitmap(src, null, dest, paint)
                }
                TrackMode.TILE -> {
                    val paintTile = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = BitmapShader(src, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP)
                    }
                    canvas.drawRect(left, top, right, bottom, paintTile)
                }
            }
        }

        private fun decodeSmall(bytes: ByteArray, maxPx: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }

        private fun createRoundedBackground(
            context: Context,
            color: Int,
            radiusDp: Int,
            widthDp: Int,
            heightDp: Int
        ): Bitmap {
            val density = context.resources.displayMetrics.density
            val widthPx = (widthDp * density).toInt().coerceAtLeast(100)
            val heightPx = (heightDp * density).toInt().coerceAtLeast(100)
            val key = BackgroundKey(color, radiusDp, widthPx, heightPx)
            backgroundCache.get(key)?.let { return it }

            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.FILL
            }
            val rect = RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat())
            val radiusPx = radiusDp * density
            canvas.drawRoundRect(rect, radiusPx, radiusPx, paint)
            backgroundCache.put(key, bitmap)
            return bitmap
        }

        private fun setupPendingIntents(context: Context, views: RemoteViews) {
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

            val openPlayerIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val openPlayerPendingIntent = PendingIntent.getActivity(
                context, 6, openPlayerIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_text_container, openPlayerPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_artwork, openPlayerPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_progress_click_target, openPlayerPendingIntent)
        }
    }
}
