package dev.grixo.nomad.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import dev.grixo.nomad.R

object NotificationHelper {
    /** Short-lived channel used only while a 15-min location ping runs. */
    const val PING_CHANNEL_ID = "nomad_location_ping_v1"
    const val PING_NOTIFICATION_ID = 2

    fun ensurePingChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PING_CHANNEL_ID,
                context.getString(R.string.tracking_ping_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.tracking_ping_channel_desc)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    fun createNotificationChannel(context: Context) {
        ensurePingChannel(context)
    }
}
