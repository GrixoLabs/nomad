package dev.grixo.nomad.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.grixo.nomad.R

object NotificationHelper {
    const val CHANNEL_ID = "nomad_tracking_channel"
    const val NOTIFICATION_ID = 1

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.tracking_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.tracking_channel_desc)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun getNotification(context: Context, contentText: String): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.tracking_notification_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
