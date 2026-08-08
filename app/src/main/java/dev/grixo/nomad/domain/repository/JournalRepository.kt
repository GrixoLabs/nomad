package dev.grixo.nomad.domain.repository

import dev.grixo.nomad.data.network.model.HistoryResponse
import dev.grixo.nomad.data.network.model.JournalEntryResponse
import dev.grixo.nomad.data.network.model.MapConfigResponse

interface JournalRepository {
    fun isJournalUnlocked(): Boolean
    suspend fun canWriteJournal(): Boolean
    suspend fun createEntry(
        body: String,
        latitude: Double,
        longitude: Double,
        placeLabel: String?
    ): Result<JournalEntryResponse>

    suspend fun loadHistory(days: Int): Result<HistoryResponse>
    suspend fun loadMapConfig(): Result<MapConfigResponse>
}
