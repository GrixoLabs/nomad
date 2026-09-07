package dev.grixo.nomad.location

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.grixo.nomad.receiver.GeofenceReceiver
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Large exit geofence around the last ping. Leaving the area wakes
 * [GeofenceReceiver] for an early location ping between the regular
 * 15-minute WorkManager cadence.
 */
@Singleton
class GeofenceHelper @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val client by lazy { LocationServices.getGeofencingClient(context) }

    fun refreshAround(latitude: Double, longitude: Double) {
        if (!hasFineLocation()) {
            Timber.d("Geofence: no fine location permission — skip")
            return
        }
        val intent = pendingIntent()
        client.removeGeofences(intent)
            .addOnCompleteListener {
                addFence(latitude, longitude, intent)
            }
    }

    fun clear() {
        if (!hasFineLocation()) return
        client.removeGeofences(pendingIntent())
            .addOnSuccessListener { Timber.d("Geofence cleared") }
            .addOnFailureListener { e -> Timber.w(e, "Geofence clear failed") }
    }

    private fun pendingIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, GeofenceReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

    private fun addFence(
        latitude: Double,
        longitude: Double,
        intent: PendingIntent
    ) {
        val geofence = Geofence.Builder()
            .setRequestId(GEOFENCE_ID)
            .setCircularRegion(latitude, longitude, RADIUS_METERS)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofence(geofence)
            .build()
        try {
            client.addGeofences(request, intent)
                .addOnSuccessListener {
                    Timber.i(
                        "Geofence armed at %.5f,%.5f r=%.0fm",
                        latitude,
                        longitude,
                        RADIUS_METERS
                    )
                }
                .addOnFailureListener { e ->
                    Timber.w(e, "Geofence arm failed")
                }
        } catch (se: SecurityException) {
            Timber.e(se, "Geofence permission denied")
        }
    }

    private fun hasFineLocation(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val GEOFENCE_ID = "nomad_tracking_exit"
        const val REQUEST_CODE = 42
        /** ~1.5 km — significant movement without constant GPS. */
        const val RADIUS_METERS = 1500f
    }
}
