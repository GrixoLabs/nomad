package dev.grixo.nomad.worker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.grixo.nomad.data.datastore.PreferenceManager
import dev.grixo.nomad.domain.repository.SignalRepository
import dev.grixo.nomad.service.TrackingService
import kotlinx.coroutines.flow.first
import timber.log.Timber

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val signalRepository: SignalRepository,
    private val preferenceManager: PreferenceManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Timber.d("SyncWorker starting")
        ensureTrackingAlive()
        val pendingBefore = signalRepository.getOfflineSignalCount()
        val result = signalRepository.syncSignals()
        return if (result.isSuccess) {
            val pendingAfter = signalRepository.getOfflineSignalCount()
            if (pendingBefore > 0 && pendingAfter < pendingBefore) {
                preferenceManager.setLastUploadEpochMs(System.currentTimeMillis())
            }
            Timber.d("SyncWorker success (pending %d -> %d)", pendingBefore, pendingAfter)
            Result.success()
        } else {
            Timber.w(result.exceptionOrNull(), "SyncWorker failure")
            Result.retry()
        }
    }

    /** Periodic safety net: revive FGS if trackingEnabled but service died. */
    private suspend fun ensureTrackingAlive() {
        if (!preferenceManager.trackingEnabled.first()) return
        val fine = ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return
        Timber.d("SyncWorker: ensuring TrackingService is running")
        ContextCompat.startForegroundService(
            applicationContext,
            Intent(applicationContext, TrackingService::class.java)
        )
    }
}
