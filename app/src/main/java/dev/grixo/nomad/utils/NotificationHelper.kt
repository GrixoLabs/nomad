package dev.grixo.nomad.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.grixo.nomad.MainActivity
import dev.grixo.nomad.R
import dev.grixo.nomad.service.TrackingService

object NotificationHelper {
    /** New channel id so IMPORTANCE_MIN applies (channel importance is immutable). */
    const val CHANNEL_ID = "nomad_tracking_quiet_v2"
    const val NOTIFICATION_ID = 1

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.tracking_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = context.getString(R.string.tracking_channel_desc)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun getNotification(context: Context): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val hideIntent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_HIDE_NOTIFICATION
        }
        val hidePending = PendingIntent.getService(
            context,
            1,
            hideIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.ic_notification
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.tracking_notification_title))
            // Status-bar glyph must be a white alpha mask; shade shows the full logo on white.
            .setSmallIcon(R.drawable.ic_stat_nomad)
            .setLargeIcon(largeIcon)
            .setContentIntent(openApp)
            .setOngoing(false)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setColor(ContextCompat.getColor(context, R.color.midnight_blue))
            .addAction(
                0,
                context.getString(R.string.tracking_notification_hide),
                hidePending
            )
            .build()
    }
}
