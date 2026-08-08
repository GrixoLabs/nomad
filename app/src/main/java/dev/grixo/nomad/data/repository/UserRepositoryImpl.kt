package dev.grixo.nomad.data.repository

import dev.grixo.nomad.data.datastore.PreferenceManager
import dev.grixo.nomad.data.network.NomadApi
import dev.grixo.nomad.data.network.model.UserRegistrationRequest
import dev.grixo.nomad.domain.model.OnboardingStatus
import dev.grixo.nomad.domain.model.UserProfile
import dev.grixo.nomad.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val preferenceManager: PreferenceManager,
    private val api: NomadApi
) : UserRepository {

    override fun observeOnboardingStatus(): Flow<OnboardingStatus> =
        preferenceManager.onboardingStatus

    override fun observeProfile(): Flow<UserProfile?> = preferenceManager.userProfile

    override fun observeJournalEnabled(): Flow<Boolean> = preferenceManager.journalEnabled

    override suspend fun register(profile: UserProfile): Result<Unit> {
        val email = profile.email?.trim()?.takeIf { it.isNotEmpty() }
        val phone = profile.phone?.trim()?.takeIf { it.isNotEmpty() }
        if (email == null && phone == null) {
            return Result.failure(IllegalArgumentException("Email or phone is required"))
        }
        if (profile.name.isBlank()) {
            return Result.failure(IllegalArgumentException("Name is required"))
        }
        if (profile.age !in 13..120) {
            return Result.failure(IllegalArgumentException("Age must be between 13 and 120"))
        }

        // Persist locally first — backend user registration is not live yet.
        preferenceManager.saveUserProfile(
            profile.copy(email = email, phone = phone, name = profile.name.trim())
        )

        return try {
            val deviceUuid = preferenceManager.deviceUuid.first()
            if (deviceUuid != null) {
                val response = api.registerUser(
                    UserRegistrationRequest(
                        device_uuid = deviceUuid,
                        name = profile.name.trim(),
                        email = email,
                        phone = phone,
                        age = profile.age,
                        gender = profile.gender.name
                    )
                )
                if (!response.isSuccessful) {
                    Timber.w("User registration API unavailable (${response.code()}); saved locally")
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.w(e, "User registration API call failed; profile kept locally")
            Result.success(Unit)
        }
    }

    override suspend fun skipRegistration() {
        preferenceManager.skipRegistration()
    }

    override suspend fun logout() {
        preferenceManager.logout()
    }
}
