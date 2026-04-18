package org.itxsvv.kxradar

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import org.itxsvv.kxradar.ui.dataStore

private val settingsKey = stringPreferencesKey("settings_v2")

class RadarSettingsService(
    private val context: Context,
) {
    val settings: Flow<RadarSettings> = context.dataStore.data
        .map { preferences ->
            try {
                jsonWithUnknownKeys.decodeFromString<RadarSettings>(
                    preferences[settingsKey] ?: RadarSettings.defaultSettings,
                )
            } catch (error: Throwable) {
                Log.e(KarooRadarExtension.TAG, "Failed to read preferences", error)
                RadarSettings()
            }
        }
        .distinctUntilChanged()

    suspend fun save(settings: RadarSettings) {
        context.dataStore.edit { preferences ->
            preferences[settingsKey] = jsonWithUnknownKeys.encodeToString(settings)
        }
    }
}
