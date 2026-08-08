package dev.grixo.nomad.data.repository

import dev.grixo.nomad.data.datastore.PreferenceManager
import dev.grixo.nomad.data.network.NomadApi
import dev.grixo.nomad.data.network.model.HistoryResponse
import dev.grixo.nomad.data.network.model.JournalCreateRequest
import dev.grixo.nomad.data.network.model.JournalEntryResponse
import dev.grixo.nomad.data.network.model.MapConfigResponse
import dev.grixo.nomad.domain.repository.JournalRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JournalRepositoryImpl @Inject constructor(
    private val preferenceManager: PreferenceManager,
    private val api: NomadApi
) : JournalRepository {

    @Volatile
    private var unlockedCache: Boolean = false

    override fun isJournalUnlocked(): Boolean = unlockedCache

    override suspend fun canWriteJournal(): Boolean {
        val enabled = preferenceManager.journalEnabled.first()
        unlockedCache = enabled
        return enabled
    }

    override suspend fun createEntry(
        body: String,
        latitude: Double,
        longitude: Double,
        placeLabel: String?
    ): Result<JournalEntryResponse> {
        if (!canWriteJournal()) {
            return Result.failure(IllegalStateException("Journal locked — register to unlock"))
        }
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Entry is empty"))
        if (trimmed.length > 500) {
            return Result.failure(IllegalArgumentException("Max 500 characters"))
        }
        val deviceUuid = preferenceManager.deviceUuid.first()
            ?: return Result.failure(IllegalStateException("Device not ready"))
        return try {
            val response = api.createJournal(
                JournalCreateRequest(
                    device_uuid = deviceUuid,
                    body = trimmed,
                    latitude = latitude,
                    longitude = longitude,
                    place_label = placeLabel
                )
            )
            val payload = response.body()
            if (response.isSuccessful && payload != null) Result.success(payload)
            else Result.failure(IllegalStateException("Save failed (${response.code()})"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadHistory(days: Int): Result<HistoryResponse> {
        val deviceUuid = preferenceManager.deviceUuid.first()
            ?: return Result.failure(IllegalStateException("Device not ready"))
        return try {
            val response = api.getHistory(deviceUuid, days.coerceIn(1, 14))
            val payload = response.body()
            if (response.isSuccessful && payload != null) Result.success(payload)
            else Result.failure(IllegalStateException("History failed (${response.code()})"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadMapConfig(): Result<MapConfigResponse> {
        return try {
            val response = api.mapConfig()
            val payload = response.body()
            if (response.isSuccessful && payload != null) Result.success(payload)
            else Result.failure(IllegalStateException("Map config failed (${response.code()})"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
