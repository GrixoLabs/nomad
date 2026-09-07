package dev.grixo.nomad.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import dev.grixo.nomad.data.datastore.PreferenceManager
import dev.grixo.nomad.worker.TrackingScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

/**
 * Wakes an early [dev.grixo.nomad.worker.LocationPingWorker] when the device
 * exits the last-armed tracking geofence (significant movement).
 */
@AndroidEntryPoint
class GeofenceReceiver : BroadcastReceiver() {

    @Inject
    lateinit var preferenceManager: PreferenceManager

    override fun onReceive(context: Context, intent: Intent?) {
        val event = GeofencingEvent.fromIntent(intent ?: return) ?: return
        if (event.hasError()) {
            Timber.w("Geofence error code=%s", event.errorCode)
            return
        }
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_EXIT) return

        val pendingResult = goAsync()
        try {
            val enabled = runBlocking { preferenceManager.trackingEnabled.first() }
            if (!enabled) {
                Timber.d("Geofence exit but tracking off — ignore")
                return
            }
            Timber.i("Geofence exit — scheduling immediate location ping")
            TrackingScheduler.enqueueImmediatePing(context)
        } catch (t: Throwable) {
            Timber.e(t, "GeofenceReceiver failed")
        } finally {
            pendingResult.finish()
        }
    }
}
