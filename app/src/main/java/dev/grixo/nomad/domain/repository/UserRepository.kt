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

    /** Sign in with email or phone + password. */
    suspend fun login(email: String?, phone: String?, password: String): Result<Unit>

    /** Request password-reset OTP via email or phone. */
    suspend fun forgotPassword(email: String?, phone: String?): Result<Unit>

    /** Confirm reset OTP and set a new password. */
    suspend fun resetPassword(
        email: String?,
        phone: String?,
        otp: String,
        newPassword: String
    ): Result<Unit>

    suspend fun skipRegistration()
    suspend fun logout()
}
