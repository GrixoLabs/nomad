package dev.grixo.nomad.data.repository

import dev.grixo.nomad.data.datastore.PreferenceManager
import dev.grixo.nomad.domain.repository.JournalRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JournalRepositoryImpl @Inject constructor(
    private val preferenceManager: PreferenceManager
) : JournalRepository {

    @Volatile
    private var unlockedCache: Boolean = false

    override fun isJournalUnlocked(): Boolean = unlockedCache

    override suspend fun canWriteJournal(): Boolean {
        val enabled = preferenceManager.journalEnabled.first()
        unlockedCache = enabled
        return enabled
    }
}
