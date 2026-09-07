package dev.grixo.nomad.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.grixo.nomad.MainActivity
import dev.grixo.nomad.R
import dev.grixo.nomad.receiver.NotificationDeleteReceiver

object NotificationHelper {
    /**
     * Channel id bumped so IMPORTANCE_LOW applies (channel importance is immutable).
     * LOW = shade entry, no heads-up — required for quiet location FGS.
     */
    const val CHANNEL_ID = "nomad_tracking_quiet_v3"
    const val NOTIFICATION_ID = 1

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.tracking_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.tracking_channel_desc)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * @param prominent when false, use a minimal “tracking quietly” notification.
     * Always keeps the location FGS promoted — never use stopForeground to “hide”.
     * Ongoing + autoCancel(false) + deleteIntent: if the user clears the shade entry
     * (allowed on Android 14+), [NotificationDeleteReceiver] restarts the FGS.
     */
    fun getNotification(context: Context, prominent: Boolean = true): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val recreateOnDelete = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, NotificationDeleteReceiver::class.java)
                .setAction(NotificationDeleteReceiver.ACTION_NOTIFICATION_DELETED),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (prominent) {
            context.getString(R.string.tracking_notification_title)
        } else {
            context.getString(R.string.tracking_notification_silent_title)
        }
        val text = if (prominent) {
            context.getString(R.string.tracking_notification_body)
        } else {
            context.getString(R.string.tracking_notification_silent_body)
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openApp)
            .setDeleteIntent(recreateOnDelete)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            // Deferred avoids a heads-up “pop” each time we refresh the FGS entry.
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .build()
    }
}
