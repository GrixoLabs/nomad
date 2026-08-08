package dev.grixo.nomad.data.repository

import dev.grixo.nomad.data.datastore.PreferenceManager
import dev.grixo.nomad.data.network.NomadApi
import dev.grixo.nomad.data.network.model.AuthRegisterRequest
import dev.grixo.nomad.data.network.model.ForgotPasswordRequest
import dev.grixo.nomad.data.network.model.LoginRequest
import dev.grixo.nomad.data.network.model.ResetPasswordRequest
import dev.grixo.nomad.data.network.model.SendEmailOtpRequest
import dev.grixo.nomad.data.network.model.SendSmsOtpRequest
import dev.grixo.nomad.data.network.model.VerifyEmailOtpRequest
import dev.grixo.nomad.data.network.model.VerifySmsOtpRequest
import dev.grixo.nomad.domain.model.Gender
import dev.grixo.nomad.domain.model.OnboardingStatus
import dev.grixo.nomad.domain.model.UserProfile
import dev.grixo.nomad.domain.repository.UserRepository
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
        if (password.length < 8) {
            return Result.failure(IllegalArgumentException("Password must be at least 8 characters"))
        }

        val deviceUuid = preferenceManager.deviceUuid.first()
        return try {
            val response = api.authRegister(
                AuthRegisterRequest(
                    name = profile.name.trim(),
                    email = email,
                    phone_number = phone,
                    password = password,
                    confirm_password = password,
                    age = profile.age,
                    gender = profile.gender.name,
                    device_uuid = deviceUuid
                )
            )
            if (!response.isSuccessful) {
                val detail = response.errorBody()?.string()?.take(200)
                return Result.failure(
                    IllegalStateException(detail ?: "Registration failed (${response.code()})")
                )
            }

            pendingProfile = profile.copy(email = email, phone = phone, name = profile.name.trim())
            pendingChannel = if (email != null) "email" else "phone"

            val otpSent = if (email != null) {
                api.sendEmailOtp(SendEmailOtpRequest(email))
            } else {
                api.sendSmsOtp(SendSmsOtpRequest(phone!!))
            }
            if (!otpSent.isSuccessful) {
                return Result.failure(
                    IllegalStateException("Account created but OTP send failed (${otpSent.code()})")
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun verifyRegistrationOtp(otp: String): Result<Unit> {
        val profile = pendingProfile
            ?: return Result.failure(IllegalStateException("No pending registration"))
        val code = otp.trim()
        if (code.length < 4) {
            return Result.failure(IllegalArgumentException("Enter the verification code"))
        }
        return try {
            val response = when (pendingChannel) {
                "email" -> api.verifyEmailOtp(
                    VerifyEmailOtpRequest(email = profile.email!!, otp = code)
                )
                "phone" -> api.verifySmsOtp(
                    VerifySmsOtpRequest(phone_number = profile.phone!!, otp = code)
                )
                else -> return Result.failure(IllegalStateException("No pending registration"))
            }
            if (!response.isSuccessful) {
                return Result.failure(
                    IllegalStateException("Invalid or expired code (${response.code()})")
                )
            }
            preferenceManager.saveUserProfile(profile, journalEnabled = true)
            pendingProfile = null
            pendingChannel = null
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun resendRegistrationOtp(): Result<Unit> {
        val profile = pendingProfile
            ?: return Result.failure(IllegalStateException("No pending registration"))
        return try {
            val response = when (pendingChannel) {
                "email" -> api.sendEmailOtp(SendEmailOtpRequest(profile.email!!))
                "phone" -> api.sendSmsOtp(SendSmsOtpRequest(profile.phone!!))
                else -> return Result.failure(IllegalStateException("No pending registration"))
            }
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(IllegalStateException("Could not resend code (${response.code()})"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun login(email: String?, phone: String?, password: String): Result<Unit> {
        val cleanEmail = email?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val cleanPhone = phone?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanEmail == null && cleanPhone == null) {
            return Result.failure(IllegalArgumentException("Email or phone is required"))
        }
        if (password.length < 8) {
            return Result.failure(IllegalArgumentException("Password must be at least 8 characters"))
        }
        return try {
            val response = api.login(
                LoginRequest(
                    email = cleanEmail,
                    phone_number = cleanPhone,
                    password = password
                )
            )
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                val detail = response.errorBody()?.string()?.take(200)
                return Result.failure(
                    IllegalStateException(detail ?: "Sign in failed (${response.code()})")
                )
            }
            val profile = UserProfile(
                name = body.name?.takeIf { it.isNotBlank() } ?: "Traveler",
                email = body.email ?: cleanEmail,
                phone = body.phone_number ?: cleanPhone,
                age = body.age?.takeIf { it in 13..120 } ?: 18,
                gender = Gender.fromStorage(body.gender) ?: Gender.PREFER_NOT
            )
            preferenceManager.saveAuthTokens(body.access_token, body.refresh_token)
            preferenceManager.saveUserProfile(profile, journalEnabled = body.journal_enabled)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun forgotPassword(email: String?, phone: String?): Result<Unit> {
        val cleanEmail = email?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val cleanPhone = phone?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanEmail == null && cleanPhone == null) {
            return Result.failure(IllegalArgumentException("Email or phone is required"))
        }
        return try {
            val response = api.forgotPassword(
                ForgotPasswordRequest(email = cleanEmail, phone_number = cleanPhone)
            )
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(
                IllegalStateException("Could not send reset code (${response.code()})")
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun resetPassword(
        email: String?,
        phone: String?,
        otp: String,
        newPassword: String
    ): Result<Unit> {
        val cleanEmail = email?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val cleanPhone = phone?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanEmail == null && cleanPhone == null) {
            return Result.failure(IllegalArgumentException("Email or phone is required"))
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
                    phone_number = cleanPhone,
                    otp = otp.trim(),
                    new_password = newPassword,
                    confirm_password = newPassword
                )
            )
            if (response.isSuccessful) Result.success(Unit)
            else {
                val detail = response.errorBody()?.string()?.take(200)
                Result.failure(
                    IllegalStateException(detail ?: "Reset failed (${response.code()})")
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
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
