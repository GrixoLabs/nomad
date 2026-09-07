package dev.grixo.nomad

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.grixo.nomad.data.datastore.PreferenceManager
import dev.grixo.nomad.service.TrackingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class NomadApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var preferenceManager: PreferenceManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // Sticky tracking: if the user left tracking on, revive FGS as soon as
        // the process starts (covers process death that BootReceiver missed).
        resumeStickyTrackingIfNeeded()
    }

    private fun resumeStickyTrackingIfNeeded() {
        appScope.launch {
            try {
                if (!preferenceManager.trackingEnabled.first()) return@launch
                val fine = ContextCompat.checkSelfPermission(
                    this@NomadApp, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val coarse = ContextCompat.checkSelfPermission(
                    this@NomadApp, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (!fine && !coarse) {
                    Timber.w("trackingEnabled but no location permission — skip auto-resume")
                    return@launch
                }
                Timber.i("Resuming TrackingService (sticky trackingEnabled)")
                ContextCompat.startForegroundService(
                    this@NomadApp,
                    Intent(this@NomadApp, TrackingService::class.java)
                )
            } catch (t: Throwable) {
                Timber.e(t, "Failed to resume sticky tracking")
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
