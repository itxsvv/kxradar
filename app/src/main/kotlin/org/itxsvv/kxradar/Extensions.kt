package org.itxsvv.kxradar

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val jsonWithUnknownKeys = Json { ignoreUnknownKeys = true }

val settingsKey = stringPreferencesKey("settings")

@Serializable
data class RadarSettings(
    val threatLevelFreq: Int,
    val threatLevelDur: Int,
    val enabled: Boolean = true,
){
    companion object {
        val defaultSettings = Json.encodeToString(RadarSettings())
    }
    constructor() : this(200, 100, true)
}

suspend fun saveSettings(context: Context, settings: RadarSettings) {
    context.dataStore.edit { t ->
        t[settingsKey] = Json.encodeToString(settings)
    }
}

fun Context.streamSettings(): Flow<RadarSettings> {
    return dataStore.data.map { settingsJson ->
        try {
            jsonWithUnknownKeys.decodeFromString<RadarSettings>(
                settingsJson[settingsKey] ?: RadarSettings.defaultSettings
            )
        } catch(e: Throwable){
            Log.e(KarooRadarExtension.TAG, "Failed to read preferences", e)
            RadarSettings()
        }
    }.distinctUntilChanged()
}

fun KarooSystemService.streamDataFlow(dataTypeId: String): Flow<StreamState> {
    return callbackFlow {
        val listenerId = addConsumer(OnStreamState.StartStreaming(dataTypeId)) { event: OnStreamState ->
            trySendBlocking(event.state)
        }
        awaitClose {
            removeConsumer(listenerId)
        }
    }
}
