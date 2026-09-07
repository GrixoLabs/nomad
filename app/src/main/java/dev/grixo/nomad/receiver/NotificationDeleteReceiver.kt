package dev.grixo.nomad.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.grixo.nomad.data.datastore.PreferenceManager
import dev.grixo.nomad.service.TrackingService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

/**
 * Fired via [android.app.Notification.deleteIntent] when the tracking FGS shade
 * entry is cleared (possible on Android 14+ even for ongoing notifications).
 * Restarts tracking if the user has not explicitly turned it off.
 */
@AndroidEntryPoint
class NotificationDeleteReceiver : BroadcastReceiver() {

    @Inject
    lateinit var preferenceManager: PreferenceManager

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_NOTIFICATION_DELETED) return
        val pendingResult = goAsync()
        try {
            val enabled = runBlocking { preferenceManager.trackingEnabled.first() }
            if (!enabled) {
                Timber.d("Notification cleared but tracking disabled — not restarting")
                return
            }
            Timber.i("Tracking notification cleared — recreating FGS")
            ContextCompat.startForegroundService(
                context,
                Intent(context, TrackingService::class.java)
                    .setAction(TrackingService.ACTION_RECREATE_NOTIFICATION)
            )
        } catch (t: Throwable) {
            Timber.e(t, "Failed to recreate tracking after notification clear")
        } finally {
            pendingResult.finish()
        }
    }

    companion object {
        const val ACTION_NOTIFICATION_DELETED = "dev.grixo.nomad.action.NOTIFICATION_DELETED"
    }
}
