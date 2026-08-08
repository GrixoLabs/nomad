package dev.grixo.nomad.data.repository

import dev.grixo.nomad.data.datastore.PreferenceManager
import dev.grixo.nomad.data.network.NomadApi
import dev.grixo.nomad.data.network.model.DeviceRegistrationRequest
import dev.grixo.nomad.domain.repository.DeviceRepository
import dev.grixo.nomad.utils.DeviceHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepositoryImpl @Inject constructor(
    private val api: NomadApi,
    private val preferenceManager: PreferenceManager
) : DeviceRepository {

    override fun getDeviceUuid(): Flow<String?> = preferenceManager.deviceUuid

    override fun getDeviceId(): Flow<Long?> = preferenceManager.deviceId

    override suspend fun initializeDevice() {
        val currentUuid = preferenceManager.deviceUuid.first()
        if (currentUuid == null) {
            val newUuid = UUID.randomUUID().toString()
            preferenceManager.saveDeviceUuid(newUuid)
            registerDevice()
        }
    }

    override suspend fun registerDevice(): Result<Long> {
        return try {
            val uuid = preferenceManager.deviceUuid.first() ?: UUID.randomUUID().toString().also {
                preferenceManager.saveDeviceUuid(it)
            }
            
            val request = DeviceRegistrationRequest(
                device_uuid = uuid,
                device_name = DeviceHelper.getDeviceName(),
                manufacturer = DeviceHelper.getManufacturer(),
                model = DeviceHelper.getModel(),
                android_version = DeviceHelper.getAndroidVersion(),
                app_version = DeviceHelper.getAppVersion()
            )
            
            val response = api.registerDevice(request)
            if (response.isSuccessful && response.body() != null) {
                val deviceId = response.body()!!.device_id
                preferenceManager.saveDeviceId(deviceId)
                Result.success(deviceId)
            } else {
                Result.failure(Exception("Registration failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
