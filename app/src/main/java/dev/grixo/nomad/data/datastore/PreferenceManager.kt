package dev.grixo.nomad.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "nomad_prefs")

@Singleton
class PreferenceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val deviceUuidKey = stringPreferencesKey("device_uuid")
    private val deviceIdKey = longPreferencesKey("device_id")

    val deviceUuid: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[deviceUuidKey]
    }

    val deviceId: Flow<Long?> = context.dataStore.data.map { preferences ->
        preferences[deviceIdKey]
    }

    suspend fun saveDeviceUuid(uuid: String) {
        context.dataStore.edit { preferences ->
            preferences[deviceUuidKey] = uuid
        }
    }

    suspend fun saveDeviceId(id: Long) {
        context.dataStore.edit { preferences ->
            preferences[deviceIdKey] = id
        }
    }
}
