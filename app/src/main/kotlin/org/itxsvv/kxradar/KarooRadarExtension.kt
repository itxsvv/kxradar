package org.itxsvv.kxradar

import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.PlayBeepPattern
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

class KarooRadarExtension : KarooExtension("kxradar", "1.0.1") {
    companion object {
        const val TAG = "kxradar"
    }

    private lateinit var karooSystem: KarooSystemService
    private var serviceJob: Job? = null
    private var radarThreat: Boolean = false

    override fun onCreate() {
        super.onCreate()
        karooSystem = KarooSystemService(applicationContext)
        serviceJob = CoroutineScope(Dispatchers.IO).launch {
            karooSystem.connect { connected ->
                if (connected) {
                    Log.i(TAG, "Connected")
                }
            }

            val prefs = dataStore.data.map { settingsJson ->
                try {
                    jsonWithUnknownKeys.decodeFromString<RadarSettings>(
                        settingsJson[settingsKey] ?: RadarSettings.defaultSettings
                    )
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to read preferences", e)
                    RadarSettings()
                }
            }

            karooSystem.streamDataFlow(DataType.Type.RADAR)
                .mapNotNull { (it as? StreamState.Streaming)?.dataPoint?.values }
                .combine(prefs) {
                    values, settings -> values to settings
                }
                .collect({ (values, settings) ->
                    val threatLevel = values[DataType.Field.RADAR_THREAT_LEVEL] ?: 0.0
                    if (!radarThreat && threatLevel > 0) {
                        Log.i(TAG, "Beep ${settings.threatLevelDur} ${settings.threatLevelFreq}")
                        karooSystem.dispatch(
                            PlayBeepPattern(
                                listOf(PlayBeepPattern.Tone(settings.threatLevelFreq, settings.threatLevelDur))
                            )
                        )
                    }
                    radarThreat = threatLevel != 0.0
                })
        }
    }

    override fun onDestroy() {
        serviceJob?.cancel()
        serviceJob = null
        karooSystem.disconnect()
        super.onDestroy()
    }
}
