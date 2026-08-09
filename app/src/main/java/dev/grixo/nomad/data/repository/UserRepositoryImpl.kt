package dev.grixo.nomad.data.repository

import dev.grixo.nomad.data.datastore.PreferenceManager
import dev.grixo.nomad.data.network.NomadApi
import dev.grixo.nomad.data.network.model.AuthRegisterRequest
import dev.grixo.nomad.data.network.model.ForgotPasswordRequest
import dev.grixo.nomad.data.network.model.LoginRequest
import dev.grixo.nomad.data.network.model.ResetPasswordRequest
import dev.grixo.nomad.data.network.model.SendEmailOtpRequest
import dev.grixo.nomad.data.network.model.VerifyEmailOtpRequest
import dev.grixo.nomad.domain.model.Gender
import dev.grixo.nomad.domain.model.OnboardingStatus
import dev.grixo.nomad.domain.model.UserProfile
import dev.grixo.nomad.domain.repository.UserRepository
import dev.grixo.nomad.utils.AuthErrorMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val preferenceManager: PreferenceManager,
    private val api: NomadApi
) : UserRepository {

    @Volatile
    private var pendingProfile: UserProfile? = null

    @Volatile
    private var pendingChannel: String? = null // "email" or "phone"

    override fun observeOnboardingStatus(): Flow<OnboardingStatus> =
        preferenceManager.onboardingStatus

    override fun observeProfile(): Flow<UserProfile?> = preferenceManager.userProfile

    override fun observeJournalEnabled(): Flow<Boolean> = preferenceManager.journalEnabled

    override suspend fun startRegistration(profile: UserProfile, password: String): Result<Unit> {
        val email = profile.email?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it.contains('@') }
            ?: return Result.failure(IllegalArgumentException("Email is required"))
        if (profile.name.isBlank()) {
            return Result.failure(IllegalArgumentException("Name is required"))
        }
        if (profile.age !in 13..120) {
            return Result.failure(IllegalArgumentException("Age must be between 13 and 120"))
        }
        if (password.length < 8) {
            return Result.failure(IllegalArgumentException("Password must be at least 8 characters"))
        }

        val deviceUuid = preferenceManager.deviceUuid.first()
        return try {
            // Backend /auth/register creates the pending user and sends email OTP.
            // Phone/SMS registration is temporarily disabled.
            val response = api.authRegister(
                AuthRegisterRequest(
                    name = profile.name.trim(),
                    email = email,
                    phone_number = null,
                    password = password,
                    confirm_password = password,
                    age = profile.age,
                    gender = profile.gender.name,
                    device_uuid = deviceUuid
                )
            )
            if (!response.isSuccessful) {
                return Result.failure(
                    IllegalStateException(
                        AuthErrorMapper.fromResponse(response, "Registration failed")
                    )
                )
            }

            pendingProfile = profile.copy(
                email = email,
                phone = null,
                name = profile.name.trim()
            )
            pendingChannel = "email"
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(IllegalStateException(AuthErrorMapper.friendlyNetworkMessage(e), e))
        }
    }

    override suspend fun verifyRegistrationOtp(otp: String): Result<Unit> {
        val profile = pendingProfile
            ?: return Result.failure(IllegalStateException("No pending registration"))
        val email = profile.email
            ?: return Result.failure(IllegalStateException("No pending registration"))
        val code = otp.trim()
        if (code.length < 4) {
            return Result.failure(IllegalArgumentException("Enter the verification code"))
        }
        return try {
            val response = api.verifyEmailOtp(VerifyEmailOtpRequest(email = email, otp = code))
            if (!response.isSuccessful) {
                return Result.failure(
                    IllegalStateException(
                        AuthErrorMapper.fromResponse(response, "Invalid or expired code")
                    )
                )
            }
            // Local REGISTERED status only after OTP succeeds — never on failed register.
            preferenceManager.saveUserProfile(profile, journalEnabled = true)
            pendingProfile = null
            pendingChannel = null
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(IllegalStateException(AuthErrorMapper.friendlyNetworkMessage(e), e))
        }
    }

    override suspend fun resendRegistrationOtp(): Result<Unit> {
        val profile = pendingProfile
            ?: return Result.failure(IllegalStateException("No pending registration"))
        val email = profile.email
            ?: return Result.failure(IllegalStateException("No pending registration"))
        return try {
            val response = api.sendEmailOtp(SendEmailOtpRequest(email))
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(
                IllegalStateException(
                    AuthErrorMapper.fromResponse(response, "Could not resend code")
                )
            )
        } catch (e: Exception) {
            Result.failure(IllegalStateException(AuthErrorMapper.friendlyNetworkMessage(e), e))
        }
    }

    override suspend fun login(email: String?, phone: String?, password: String): Result<Unit> {
        val cleanEmail = email?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it.contains('@') }
        if (cleanEmail == null) {
            return Result.failure(IllegalArgumentException("Email is required"))
        }
        if (password.length < 8) {
            return Result.failure(IllegalArgumentException("Password must be at least 8 characters"))
        }
        return try {
            val response = api.login(
                LoginRequest(
                    email = cleanEmail,
                    phone_number = null,
                    password = password
                )
            )
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                return Result.failure(
                    IllegalStateException(
                        AuthErrorMapper.fromResponse(response, "Sign in failed")
                    )
                )
            }
            val profile = UserProfile(
                name = body.name?.takeIf { it.isNotBlank() } ?: "Traveler",
                email = body.email ?: cleanEmail,
                phone = body.phone_number,
                age = body.age?.takeIf { it in 13..120 } ?: 18,
                gender = Gender.fromStorage(body.gender) ?: Gender.PREFER_NOT
            )
            preferenceManager.saveAuthTokens(body.access_token, body.refresh_token)
            preferenceManager.saveUserProfile(profile, journalEnabled = body.journal_enabled)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(IllegalStateException(AuthErrorMapper.friendlyNetworkMessage(e), e))
        }
    }

    override suspend fun forgotPassword(email: String?, phone: String?): Result<Unit> {
        val cleanEmail = email?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it.contains('@') }
        if (cleanEmail == null) {
            return Result.failure(IllegalArgumentException("Email is required"))
        }
        return try {
            val response = api.forgotPassword(
                ForgotPasswordRequest(email = cleanEmail, phone_number = null)
            )
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(
                IllegalStateException(
                    AuthErrorMapper.fromResponse(response, "Could not send reset code")
                )
            )
        } catch (e: Exception) {
            Result.failure(IllegalStateException(AuthErrorMapper.friendlyNetworkMessage(e), e))
        }
    }

    override suspend fun resetPassword(
        email: String?,
        phone: String?,
        otp: String,
        newPassword: String
    ): Result<Unit> {
        val cleanEmail = email?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it.contains('@') }
        if (cleanEmail == null) {
            return Result.failure(IllegalArgumentException("Email is required"))
        }
        if (otp.trim().length < 4) {
            return Result.failure(IllegalArgumentException("Enter the verification code"))
        }
        if (newPassword.length < 8) {
            return Result.failure(IllegalArgumentException("Password must be at least 8 characters"))
        }
        return try {
            val response = api.resetPassword(
                ResetPasswordRequest(
                    email = cleanEmail,
                    phone_number = null,
                    otp = otp.trim(),
                    new_password = newPassword,
                    confirm_password = newPassword
                )
            )
            if (response.isSuccessful) Result.success(Unit)
            else {
                Result.failure(
                    IllegalStateException(
                        AuthErrorMapper.fromResponse(response, "Reset failed")
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException(AuthErrorMapper.friendlyNetworkMessage(e), e))
        }
    }

    override suspend fun skipRegistration() {
        preferenceManager.skipRegistration()
    }

    override suspend fun logout() {
        pendingProfile = null
        pendingChannel = null
        preferenceManager.logout()
    }
}
