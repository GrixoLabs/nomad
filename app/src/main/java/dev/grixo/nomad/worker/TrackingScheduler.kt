package dev.grixo.nomad.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules infrequent location pings (no continuous GPS / no always-on notification).
 * Periodic interval is 15 minutes (WorkManager minimum for periodic work).
 */
object TrackingScheduler {
    const val PERIODIC_PING = "nomad_location_ping_periodic"
    const val ONE_SHOT_PING = "nomad_location_ping_once"
    const val PERIODIC_SYNC = "nomad_periodic_signal_sync"
    const val ONE_SHOT_SYNC = "nomad_signal_sync_once"

    fun startTracking(workManager: WorkManager) {
        enqueuePeriodicPing(workManager)
        enqueueImmediatePing(workManager)
        // Keep offline-queue drain as a backup cadence.
        enqueuePeriodicSync(workManager)
    }

    fun startTracking(context: Context) {
        startTracking(WorkManager.getInstance(context))
    }

    fun stopTracking(workManager: WorkManager) {
        workManager.cancelUniqueWork(PERIODIC_PING)
        workManager.cancelUniqueWork(ONE_SHOT_PING)
        workManager.cancelUniqueWork(PERIODIC_SYNC)
        workManager.cancelUniqueWork(ONE_SHOT_SYNC)
    }

    fun stopTracking(context: Context) {
        stopTracking(WorkManager.getInstance(context))
    }

    fun enqueueImmediatePing(workManager: WorkManager) {
        val request = OneTimeWorkRequestBuilder<LocationPingWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueueUniqueWork(
            ONE_SHOT_PING,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun enqueueImmediatePing(context: Context) {
        enqueueImmediatePing(WorkManager.getInstance(context))
    }

    private fun enqueuePeriodicPing(workManager: WorkManager) {
        val request = PeriodicWorkRequestBuilder<LocationPingWorker>(15, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_PING,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun enqueuePeriodicSync(workManager: WorkManager) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
