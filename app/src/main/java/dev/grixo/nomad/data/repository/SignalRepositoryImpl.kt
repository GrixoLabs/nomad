package dev.grixo.nomad.data.repository

import dev.grixo.nomad.data.database.dao.SignalDao
import dev.grixo.nomad.data.database.entity.SignalEntity
import dev.grixo.nomad.data.datastore.PreferenceManager
import dev.grixo.nomad.data.network.NomadApi
import dev.grixo.nomad.data.network.model.SignalRequest
import dev.grixo.nomad.domain.repository.SignalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SignalRepositoryImpl @Inject constructor(
    private val api: NomadApi,
    private val signalDao: SignalDao,
    private val preferenceManager: PreferenceManager
) : SignalRepository {

    override suspend fun saveSignal(signal: SignalEntity) {
        signalDao.insertSignal(signal)
    }

    override fun getAllOfflineSignals(): Flow<List<SignalEntity>> = signalDao.getAllSignals()

    override suspend fun sendSignalDirectly(signal: SignalEntity): Result<Unit> {
        return try {
            val uuid = preferenceManager.deviceUuid.first()
                ?: return Result.failure(Exception("No device UUID"))
            val response = api.sendSignal(signal.toRequest(uuid))
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Signal send failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncSignals(): Result<Unit> {
        return try {
            val uuid = preferenceManager.deviceUuid.first()
                ?: return Result.failure(Exception("No device UUID"))
            val offlineSignals = signalDao.getSignalsChunk()
            if (offlineSignals.isEmpty()) return Result.success(Unit)

            var successCount = 0
            for (signal in offlineSignals) {
                val response = api.sendSignal(signal.toRequest(uuid))
                if (response.isSuccessful) {
                    signalDao.deleteSignalById(signal.id)
                    successCount++
                }
            }

            when {
                successCount == offlineSignals.size -> Result.success(Unit)
                successCount > 0 -> Result.success(Unit)
                else -> Result.failure(Exception("All sync attempts failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun SignalEntity.toRequest(uuid: String) = SignalRequest(
        device_uuid = uuid,
        gps_timestamp_utc = gpsTimestampUtc,
        latitude = kotlin.math.round(latitude * 1e5) / 1e5,
        longitude = kotlin.math.round(longitude * 1e5) / 1e5,
        accuracy_m = accuracyM,
        altitude_m = altitudeM,
        speed_mps = speedMps,
        bearing_deg = bearingDeg,
        battery_percent = batteryPercent,
        charging = charging,
        battery_temperature = batteryTemperature,
        network_type = networkType,
        wifi_enabled = wifiEnabled,
        bluetooth_enabled = bluetoothEnabled,
        screen_on = screenOn,
        power_save_mode = powerSaveMode
    )
}
