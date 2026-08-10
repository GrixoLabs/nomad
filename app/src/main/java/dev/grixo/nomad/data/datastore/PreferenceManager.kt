package dev.grixo.nomad.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.grixo.nomad.domain.model.Gender
import dev.grixo.nomad.domain.model.OnboardingStatus
import dev.grixo.nomad.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "nomad_prefs")

@Singleton
class PreferenceManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val deviceUuidKey = stringPreferencesKey("device_uuid")
    private val deviceIdKey = longPreferencesKey("device_id")
    private val onboardingStatusKey = stringPreferencesKey("onboarding_status")
    private val userNameKey = stringPreferencesKey("user_name")
    private val userEmailKey = stringPreferencesKey("user_email")
    private val userPhoneKey = stringPreferencesKey("user_phone")
    private val userAgeKey = intPreferencesKey("user_age")
    private val userGenderKey = stringPreferencesKey("user_gender")
    private val journalEnabledKey = booleanPreferencesKey("journal_enabled")
    private val accessTokenKey = stringPreferencesKey("access_token")
    private val refreshTokenKey = stringPreferencesKey("refresh_token")
    private val trackingEnabledKey = booleanPreferencesKey("tracking_enabled")
    private val trackingNotificationVisibleKey =
        booleanPreferencesKey("tracking_notification_visible")
    private val lastUploadEpochMsKey = longPreferencesKey("last_upload_epoch_ms")

    val deviceUuid: Flow<String?> = context.dataStore.data.map { it[deviceUuidKey] }
    val deviceId: Flow<Long?> = context.dataStore.data.map { it[deviceIdKey] }

    val trackingEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[trackingEnabledKey] == true
    }

    /**
     * When false, TrackingService uses a minimal quiet FGS status (still required by
     * Android for background location). Never maps to stopForeground.
     */
    val trackingNotificationVisible: Flow<Boolean> = context.dataStore.data.map {
        it[trackingNotificationVisibleKey] != false
    }

    val lastUploadEpochMs: Flow<Long?> = context.dataStore.data.map {
        it[lastUploadEpochMsKey]
    }

    val onboardingStatus: Flow<OnboardingStatus> = context.dataStore.data.map { prefs ->
        when (prefs[onboardingStatusKey]) {
            OnboardingStatus.REGISTERED.name -> OnboardingStatus.REGISTERED
            OnboardingStatus.SKIPPED.name -> OnboardingStatus.SKIPPED
            else -> OnboardingStatus.PENDING
        }
    }

    val journalEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[journalEnabledKey] == true
    }

    val userProfile: Flow<UserProfile?> = context.dataStore.data.map { prefs ->
        val name = prefs[userNameKey] ?: return@map null
        val age = prefs[userAgeKey] ?: return@map null
        val gender = Gender.fromStorage(prefs[userGenderKey]) ?: return@map null
        UserProfile(
            name = name,
            email = prefs[userEmailKey],
            phone = prefs[userPhoneKey],
            age = age,
            gender = gender
        )
    }

    suspend fun saveDeviceUuid(uuid: String) {
        context.dataStore.edit { it[deviceUuidKey] = uuid }
    }

    suspend fun saveDeviceId(id: Long) {
        context.dataStore.edit { it[deviceIdKey] = id }
    }

    suspend fun saveAuthTokens(accessToken: String, refreshToken: String) {
        context.dataStore.edit { prefs ->
            prefs[accessTokenKey] = accessToken
            prefs[refreshTokenKey] = refreshToken
        }
    }

    suspend fun saveUserProfile(profile: UserProfile, journalEnabled: Boolean = true) {
        context.dataStore.edit { prefs ->
            prefs[onboardingStatusKey] = OnboardingStatus.REGISTERED.name
            prefs[userNameKey] = profile.name
            prefs[userAgeKey] = profile.age
            prefs[userGenderKey] = profile.gender.name
            prefs[journalEnabledKey] = journalEnabled
            if (profile.email.isNullOrBlank()) prefs.remove(userEmailKey)
            else prefs[userEmailKey] = profile.email.trim()
            if (profile.phone.isNullOrBlank()) prefs.remove(userPhoneKey)
            else prefs[userPhoneKey] = profile.phone.trim()
        }
    }

    suspend fun setTrackingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[trackingEnabledKey] = enabled }
    }

    suspend fun setTrackingNotificationVisible(visible: Boolean) {
        context.dataStore.edit { it[trackingNotificationVisibleKey] = visible }
    }

    suspend fun setLastUploadEpochMs(epochMs: Long) {
        context.dataStore.edit { it[lastUploadEpochMsKey] = epochMs }
    }

    suspend fun skipRegistration() {
        context.dataStore.edit { prefs ->
            prefs[onboardingStatusKey] = OnboardingStatus.SKIPPED.name
            prefs[journalEnabledKey] = false
        }
    }

    /** Clears profile / journal unlock; keeps device identity for signal continuity. */
    suspend fun logout() {
        context.dataStore.edit { prefs ->
            prefs[onboardingStatusKey] = OnboardingStatus.PENDING.name
            prefs[journalEnabledKey] = false
            prefs[trackingEnabledKey] = false
            prefs.remove(userNameKey)
            prefs.remove(userEmailKey)
            prefs.remove(userPhoneKey)
            prefs.remove(userAgeKey)
            prefs.remove(userGenderKey)
            prefs.remove(accessTokenKey)
            prefs.remove(refreshTokenKey)
        }
    }
}
