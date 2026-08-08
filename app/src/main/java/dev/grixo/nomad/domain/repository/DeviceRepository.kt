package dev.grixo.nomad.domain.repository

import kotlinx.coroutines.flow.Flow

interface DeviceRepository {
    fun getDeviceUuid(): Flow<String?>
    fun getDeviceId(): Flow<Long?>
    suspend fun registerDevice(): Result<Long>
    suspend fun initializeDevice()
}
