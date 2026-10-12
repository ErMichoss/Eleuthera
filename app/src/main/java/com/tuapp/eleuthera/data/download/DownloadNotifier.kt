package com.tuapp.eleuthera.data.download

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Notificación de progreso de cada descarga, con botón Cancelar. */
@Singleton
class DownloadNotifier @Inject constructor(@ApplicationContext private val ctx: Context) {

    private val manager = NotificationManagerCompat.from(ctx)

    init {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Descargas")
                .build()
        )
    }

    /** [percent] negativo = en cola o preparando (barra indeterminada). */
    private fun build(workId: UUID, title: String, percent: Int): Notification =
        NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(if (percent < 0) "En cola…" else "$percent %")
            .setProgress(100, percent.coerceAtLeast(0), percent < 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                0, "Cancelar",
                WorkManager.getInstance(ctx).createCancelPendingIntent(workId),
            )
            .build()

    fun foregroundInfo(workId: UUID, title: String, percent: Int) = ForegroundInfo(
        workId.hashCode(),
        build(workId, title, percent),
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
    )

    @SuppressLint("MissingPermission")
    fun update(workId: UUID, title: String, percent: Int) {
        if (manager.areNotificationsEnabled()) {
            manager.notify(workId.hashCode(), build(workId, title, percent))
        }
    }

    private companion object { const val CHANNEL = "downloads" }
}
