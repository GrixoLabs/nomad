package dev.grixo.nomad.domain.repository

/**
 * Phase 2 placeholder. Journal entries (place notes, max 500 chars)
 * will be wired with maps. Writing is blocked when registration was skipped.
 */
interface JournalRepository {
    fun isJournalUnlocked(): Boolean
    suspend fun canWriteJournal(): Boolean
}
