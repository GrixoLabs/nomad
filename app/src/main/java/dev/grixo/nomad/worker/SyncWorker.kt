package dev.grixo.nomad.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.grixo.nomad.domain.repository.SignalRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val signalRepository: SignalRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Timber.d("SyncWorker starting")
        val result = signalRepository.syncSignals()
        return if (result.isSuccess) {
            Timber.d("SyncWorker success")
            Result.success()
        } else {
            Timber.w(result.exceptionOrNull(), "SyncWorker failure")
            Result.retry()
        }
    }
}
