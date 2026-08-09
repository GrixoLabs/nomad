package dev.grixo.nomad.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import dev.grixo.nomad.data.datastore.PreferenceManager
import dev.grixo.nomad.data.location.LocationBus
import dev.grixo.nomad.domain.repository.SignalRepository
import dev.grixo.nomad.utils.NotificationHelper
import dev.grixo.nomad.utils.SignalCollector
import dev.grixo.nomad.worker.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class TrackingService : Service() {

    @Inject
    lateinit var signalRepository: SignalRepository

    @Inject
    lateinit var locationBus: LocationBus

    @Inject
    lateinit var preferenceManager: PreferenceManager

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var updatesRequested = false

    override fun onCreate() {
        super.onCreate()
        Timber.d("TrackingService created")
        NotificationHelper.createNotificationChannel(this)
        // Location FGS must stay promoted for reliable background GPS uploads.
        promoteToForeground()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    Timber.d("Location received: ${location.latitude}, ${location.longitude}")
                    processLocation(location)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE_NOTIFICATION -> {
                // Cannot remove the FGS notification without demoting the service and
                // breaking background location uploads on modern Android. Keep FGS.
                Timber.i("Hide notification requested — keeping FGS so uploads continue")
                serviceScope.launch { preferenceManager.setTrackingNotificationVisible(true) }
                promoteToForeground()
                return START_STICKY
            }
            ACTION_SHOW_NOTIFICATION -> {
                serviceScope.launch { preferenceManager.setTrackingNotificationVisible(true) }
                promoteToForeground()
                return START_STICKY
            }
            ACTION_STOP_TRACKING -> {
                serviceScope.launch {
                    preferenceManager.setTrackingEnabled(false)
                    // Best-effort drain of any offline signals before teardown.
                    signalRepository.syncSignals()
                }
                SyncScheduler.cancelPeriodic(WorkManager.getInstance(this))
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH_NOW -> {
                // Sync / pull-to-refresh: force a fresh fix, upload it, drain offline queue.
                Timber.i("Refresh now — fetching location and syncing offline queue")
                ensureTrackingAndRefresh()
                return START_STICKY
            }
        }

        Timber.d("TrackingService started")
        ensureTrackingAndRefresh()
        return START_STICKY
    }

    private fun ensureTrackingAndRefresh() {
        serviceScope.launch {
            preferenceManager.setTrackingEnabled(true)
            preferenceManager.setTrackingNotificationVisible(true)
        }
        promoteToForeground()
        SyncScheduler.enqueuePeriodic(WorkManager.getInstance(this))
        SyncScheduler.enqueueOnce(WorkManager.getInstance(this))
        requestLocationUpdates()
        fetchImmediateLocation()
    }

    private fun promoteToForeground() {
        val notification = NotificationHelper.getNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID, notification)
        }
    }

    private fun fetchImmediateLocation() {
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        Timber.d("Immediate location: ${location.latitude}, ${location.longitude}")
                        processLocation(location)
                    } else {
                        fusedLocationClient.lastLocation.addOnSuccessListener { last ->
                            if (last != null) processLocation(last)
                        }
                    }
                }
        } catch (unlikely: SecurityException) {
            Timber.e(unlikely, "Lost location permission for immediate fetch.")
        }
    }

    private fun requestLocationUpdates() {
        if (updatesRequested) return
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5 * 60 * 1000L)
            .setMinUpdateIntervalMillis(60 * 1000L)
            .setMaxUpdateDelayMillis(10 * 60 * 1000L)
            .setMaxUpdates(Int.MAX_VALUE)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            updatesRequested = true
        } catch (unlikely: SecurityException) {
            Timber.e(unlikely, "Lost location permission. Could not request updates.")
        }
    }

    private fun processLocation(location: android.location.Location) {
        serviceScope.launch {
            val signal = SignalCollector.collect(this@TrackingService, location)
            val result = signalRepository.sendSignalDirectly(signal)
            val uploaded = result.isSuccess
            if (!uploaded) {
                Timber.w(result.exceptionOrNull(), "Failed to send signal directly, saving offline")
                signalRepository.saveSignal(signal)
                SyncScheduler.enqueueOnce(this@TrackingService)
            } else {
                Timber.d("Signal sent successfully")
                preferenceManager.setLastUploadEpochMs(System.currentTimeMillis())
            }
            locationBus.publish(
                locationBus.fromLocation(
                    location = location,
                    batteryPercent = signal.batteryPercent.takeIf { it >= 0 },
                    networkType = signal.networkType,
                    uploaded = uploaded
                )
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Timber.d("TrackingService destroyed")
        if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        // Queue a one-shot sync so pending offline rows are not stranded after stop.
        SyncScheduler.enqueueOnce(this)
        serviceJob.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_HIDE_NOTIFICATION = "dev.grixo.nomad.action.HIDE_NOTIFICATION"
        const val ACTION_SHOW_NOTIFICATION = "dev.grixo.nomad.action.SHOW_NOTIFICATION"
        const val ACTION_STOP_TRACKING = "dev.grixo.nomad.action.STOP_TRACKING"
        /** Force GPS fix + upload + offline drain (Sync now / pull-to-refresh). */
        const val ACTION_REFRESH_NOW = "dev.grixo.nomad.action.REFRESH_NOW"
    }
}
