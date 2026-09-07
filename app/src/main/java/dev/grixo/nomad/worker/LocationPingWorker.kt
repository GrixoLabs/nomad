package dev.grixo.nomad.worker

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.grixo.nomad.MainActivity
import dev.grixo.nomad.R
import dev.grixo.nomad.data.datastore.PreferenceManager
import dev.grixo.nomad.data.location.LocationBus
import dev.grixo.nomad.domain.repository.SignalRepository
import dev.grixo.nomad.location.GeofenceHelper
import dev.grixo.nomad.utils.NotificationHelper
import dev.grixo.nomad.utils.SignalCollector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Infrequent location ping (~15 min via [TrackingScheduler]).
 * Shows a short-lived FGS notification only while the ping runs, then stops —
 * no constant shade entry between pings.
 */
@HiltWorker
class LocationPingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val signalRepository: SignalRepository,
    private val preferenceManager: PreferenceManager,
    private val locationBus: LocationBus,
    private val geofenceHelper: GeofenceHelper
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!preferenceManager.trackingEnabled.first()) {
            Timber.d("LocationPingWorker: tracking disabled — skip")
            return Result.success()
        }
        if (!hasLocationPermission()) {
            Timber.w("LocationPingWorker: missing location permission")
            return Result.failure()
        }

        return try {
            setForeground(pingForegroundInfo())
            val location = fetchLocation()
            if (location == null) {
                Timber.w("LocationPingWorker: no location fix")
                signalRepository.syncSignals()
                return Result.retry()
            }

            val signal = SignalCollector.collect(applicationContext, location)
            val uploaded = signalRepository.sendSignalDirectly(signal).isSuccess
            if (!uploaded) {
                signalRepository.saveSignal(signal)
                signalRepository.syncSignals()
            } else {
                preferenceManager.setLastUploadEpochMs(System.currentTimeMillis())
                signalRepository.syncSignals()
            }

            locationBus.publish(
                locationBus.fromLocation(
                    location = location,
                    batteryPercent = signal.batteryPercent.takeIf { it >= 0 },
                    networkType = signal.networkType,
                    uploaded = uploaded
                )
            )

            geofenceHelper.refreshAround(location.latitude, location.longitude)

            Timber.i(
                "LocationPingWorker: ping lat=%.5f lon=%.5f uploaded=%s",
                location.latitude,
                location.longitude,
                uploaded
            )
            Result.success()
        } catch (t: Throwable) {
            Timber.e(t, "LocationPingWorker failed")
            Result.retry()
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private suspend fun fetchLocation(): Location? {
        val client = LocationServices.getFusedLocationProviderClient(applicationContext)
        return try {
            val cts = CancellationTokenSource()
            try {
                awaitTask(
                    client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                )
            } finally {
                cts.cancel()
            } ?: awaitTask(client.lastLocation)
        } catch (se: SecurityException) {
            Timber.e(se, "Location permission lost mid-ping")
            null
        } catch (t: Throwable) {
            Timber.e(t, "getCurrentLocation failed; trying lastLocation")
            try {
                awaitTask(client.lastLocation)
            } catch (_: Throwable) {
                null
            }
        }
    }

    private suspend fun <T> awaitTask(task: com.google.android.gms.tasks.Task<T>): T? =
        suspendCancellableCoroutine { cont ->
            task.addOnSuccessListener { value ->
                if (cont.isActive) cont.resume(value)
            }.addOnFailureListener { e ->
                if (cont.isActive) cont.resumeWithException(e)
            }.addOnCanceledListener {
                if (cont.isActive) cont.resume(null)
            }
        }

    private fun pingForegroundInfo(): ForegroundInfo {
        NotificationHelper.ensurePingChannel(applicationContext)
        val openApp = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(
            applicationContext,
            NotificationHelper.PING_CHANNEL_ID
        )
            .setContentTitle(applicationContext.getString(R.string.tracking_ping_title))
            .setContentText(applicationContext.getString(R.string.tracking_ping_body))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NotificationHelper.PING_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            ForegroundInfo(NotificationHelper.PING_NOTIFICATION_ID, notification)
        }
    }
}
