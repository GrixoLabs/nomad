package dev.grixo.nomad.domain.repository

import dev.grixo.nomad.data.database.entity.SignalEntity
import kotlinx.coroutines.flow.Flow

interface SignalRepository {
    suspend fun saveSignal(signal: SignalEntity)
    fun getAllOfflineSignals(): Flow<List<SignalEntity>>
    suspend fun getOfflineSignalCount(): Int
    suspend fun syncSignals(): Result<Unit>
    suspend fun sendSignalDirectly(signal: SignalEntity): Result<Unit>
}
