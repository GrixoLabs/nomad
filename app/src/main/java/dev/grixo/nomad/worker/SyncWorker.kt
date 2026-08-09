package dev.grixo.nomad.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.grixo.nomad.data.datastore.PreferenceManager
import dev.grixo.nomad.domain.repository.SignalRepository
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
}
