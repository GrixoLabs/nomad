package dev.grixo.nomad

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.grixo.nomad.data.datastore.PreferenceManager
import dev.grixo.nomad.utils.NotificationHelper
import dev.grixo.nomad.worker.TrackingScheduler
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
        NotificationHelper.ensurePingChannel(this)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
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
                Timber.i("Resuming 15-minute location ping schedule")
                TrackingScheduler.startTracking(this@NomadApp)
            } catch (t: Throwable) {
                Timber.e(t, "Failed to resume intermittent tracking")
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
