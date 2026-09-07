package dev.grixo.nomad.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.grixo.nomad.data.datastore.PreferenceManager
import dev.grixo.nomad.service.TrackingService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

/**
 * Restarts [TrackingService] after reboot / package replace when the user left
 * tracking enabled. Does not start tracking if location permission is missing.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var preferenceManager: PreferenceManager

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pendingResult = goAsync()
        try {
            val enabled = runBlocking { preferenceManager.trackingEnabled.first() }
            if (!enabled) {
                Timber.d("Boot: tracking not enabled — skip")
                return
            }
            if (!hasLocationPermission(context)) {
                Timber.w("Boot: tracking enabled but location permission missing — skip")
                return
            }
            Timber.i("Boot: restarting TrackingService (trackingEnabled=true)")
            ContextCompat.startForegroundService(
                context,
                Intent(context, TrackingService::class.java)
            )
        } catch (t: Throwable) {
            Timber.e(t, "Boot: failed to restart TrackingService")
        } finally {
            pendingResult.finish()
        }
    }

    private fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }
}
