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
        if (Build.VERSION.VERSION_ID >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Nomad Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Used for Nomad foreground tracking service"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun getNotification(context: Context, contentText: String): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Nomad Tracking Active")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use default for now
            .setOngoing(true)
            .build()
    }
}
