package dev.grixo.nomad.domain.repository

import dev.grixo.nomad.domain.model.OnboardingStatus
import dev.grixo.nomad.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun observeOnboardingStatus(): Flow<OnboardingStatus>
    fun observeProfile(): Flow<UserProfile?>
    fun observeJournalEnabled(): Flow<Boolean>
    suspend fun register(profile: UserProfile): Result<Unit>
    suspend fun skipRegistration()
    suspend fun logout()
}
