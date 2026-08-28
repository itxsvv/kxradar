package org.itxsvv.kxradar

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sunLocationDataStore by preferencesDataStore(name = "sun_location")
private val latitudeKey = doublePreferencesKey("latitude")
private val longitudeKey = doublePreferencesKey("longitude")

internal class SunLocationRepository(
    private val context: Context,
) {
    val location: Flow<SunLocation?> = context.sunLocationDataStore.data.map { preferences ->
        val latitude = preferences[latitudeKey]
        val longitude = preferences[longitudeKey]
        if (latitude == null || longitude == null) {
            null
        } else {
            SunLocation(latitude, longitude).takeIf { it.isValid() }
        }
    }

    suspend fun save(location: SunLocation) {
        if (!location.isValid()) {
            return
        }
        context.sunLocationDataStore.edit { preferences ->
            preferences[latitudeKey] = location.latitude
            preferences[longitudeKey] = location.longitude
        }
    }
}
