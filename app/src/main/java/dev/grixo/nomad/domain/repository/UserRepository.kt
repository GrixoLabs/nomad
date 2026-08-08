package dev.grixo.nomad.domain.repository

import dev.grixo.nomad.domain.model.OnboardingStatus
import dev.grixo.nomad.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun observeOnboardingStatus(): Flow<OnboardingStatus>
    fun observeProfile(): Flow<UserProfile?>
    fun observeJournalEnabled(): Flow<Boolean>

    /** Creates account on server and sends OTP. Does not unlock journal yet. */
    suspend fun startRegistration(profile: UserProfile, password: String): Result<Unit>

    /** Verifies OTP then unlocks local registered profile. */
    suspend fun verifyRegistrationOtp(otp: String): Result<Unit>

    suspend fun resendRegistrationOtp(): Result<Unit>

    suspend fun skipRegistration()
    suspend fun logout()
}
