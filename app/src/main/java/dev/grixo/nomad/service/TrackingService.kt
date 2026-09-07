package dev.grixo.nomad.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
    private var foregroundReady = false
    /** True only for explicit user/logout stop — suppresses sticky restart. */
    @Volatile
    private var intentionalStop = false
    @Volatile
    private var notificationProminent = true

    override fun onCreate() {
        super.onCreate()
        Timber.d("TrackingService created")
        intentionalStop = false
        NotificationHelper.createNotificationChannel(this)
        serviceScope.launch {
            preferenceManager.trackingNotificationVisible.collect { visible ->
                notificationProminent = visible
            }
        }
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
                // Quiet mode: keep FGS (uploads continue) but switch to minimal status.
                // Never stopForeground — that demotes location FGS on modern Android.
                Timber.i("Quiet tracking — minimal FGS notification")
                notificationProminent = false
                serviceScope.launch { preferenceManager.setTrackingNotificationVisible(false) }
                promoteToForeground()
                return START_STICKY
            }
            ACTION_SHOW_NOTIFICATION -> {
                Timber.i("Show tracking status notification")
                notificationProminent = true
                serviceScope.launch { preferenceManager.setTrackingNotificationVisible(true) }
                promoteToForeground()
                return START_STICKY
            }
            ACTION_RECREATE_NOTIFICATION -> {
                // Shade cleared (API 34+) or system demoted FGS — re-promote immediately.
                Timber.i("Recreate tracking notification / re-promote FGS")
                intentionalStop = false
                serviceScope.launch { preferenceManager.setTrackingEnabled(true) }
                promoteToForeground()
                SyncScheduler.enqueuePeriodic(WorkManager.getInstance(this))
                requestLocationUpdates()
                return START_STICKY
            }
            ACTION_STOP_TRACKING -> {
                Timber.i("Explicit stop tracking")
                intentionalStop = true
                // Persist off before stopSelf so onDestroy / receivers do not revive.
                runBlocking { preferenceManager.setTrackingEnabled(false) }
                serviceScope.launch { signalRepository.syncSignals() }
                SyncScheduler.cancelPeriodic(WorkManager.getInstance(this))
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH_NOW -> {
                // Refresh location/upload only — do not re-alert the notification.
                Timber.i("Refresh now — fetching location and syncing offline queue")
                intentionalStop = false
                serviceScope.launch { preferenceManager.setTrackingEnabled(true) }
                ensureForegroundQuietly()
                SyncScheduler.enqueuePeriodic(WorkManager.getInstance(this))
                SyncScheduler.enqueueOnce(WorkManager.getInstance(this))
                requestLocationUpdates()
                fetchImmediateLocation()
                return START_STICKY
            }
        }

        Timber.d("TrackingService started")
        intentionalStop = false
        serviceScope.launch { preferenceManager.setTrackingEnabled(true) }
        ensureForegroundQuietly()
        SyncScheduler.enqueuePeriodic(WorkManager.getInstance(this))
        SyncScheduler.enqueueOnce(WorkManager.getInstance(this))
        requestLocationUpdates()
        fetchImmediateLocation()
        return START_STICKY
    }

    private fun ensureForegroundQuietly() {
        if (foregroundReady) return
        promoteToForeground()
    }

    private fun promoteToForeground() {
        val notification = NotificationHelper.getNotification(
            this,
            prominent = notificationProminent
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID, notification)
        }
        foregroundReady = true
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

    /**
     * Swipe-away from Recents must not end tracking. Restart unless the user
     * explicitly stopped (or logged out) via [ACTION_STOP_TRACKING].
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (intentionalStop) return
        val stillEnabled = try {
            runBlocking { preferenceManager.trackingEnabled.first() }
        } catch (_: Throwable) {
            true
        }
        if (!stillEnabled) return
        Timber.i("Task removed — restarting TrackingService")
        ContextCompat.startForegroundService(
            applicationContext,
            Intent(applicationContext, TrackingService::class.java)
        )
    }

    override fun onDestroy() {
        Timber.d("TrackingService destroyed (intentionalStop=%s)", intentionalStop)
        if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        SyncScheduler.enqueueOnce(this)
        val shouldRestart = !intentionalStop && try {
            runBlocking { preferenceManager.trackingEnabled.first() }
        } catch (_: Throwable) {
            false
        }
        serviceJob.cancel()
        super.onDestroy()
        if (shouldRestart) {
            Timber.i("Service destroyed while trackingEnabled — restarting")
            ContextCompat.startForegroundService(
                applicationContext,
                Intent(applicationContext, TrackingService::class.java)
            )
        }
    }

    companion object {
        const val ACTION_HIDE_NOTIFICATION = "dev.grixo.nomad.action.HIDE_NOTIFICATION"
        const val ACTION_SHOW_NOTIFICATION = "dev.grixo.nomad.action.SHOW_NOTIFICATION"
        const val ACTION_STOP_TRACKING = "dev.grixo.nomad.action.STOP_TRACKING"
        /** Re-promote FGS after shade clear / system demotion. */
        const val ACTION_RECREATE_NOTIFICATION = "dev.grixo.nomad.action.RECREATE_NOTIFICATION"
        /** Force GPS fix + upload + offline drain (Sync now / pull-to-refresh). */
        const val ACTION_REFRESH_NOW = "dev.grixo.nomad.action.REFRESH_NOW"
    }
}
