package org.itxsvv.kxradar

import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.PlayBeepPattern
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlin.time.Duration

class KarooRadarExtension : KarooExtension("kxradar", "1.0.2") {
    companion object {
        const val TAG = "kxradar"
    }
    private var targets = mapOf(
        DataType.Field.RADAR_TARGET_1_RANGE to false,
        DataType.Field.RADAR_TARGET_2_RANGE to false,
        DataType.Field.RADAR_TARGET_3_RANGE to false,
        DataType.Field.RADAR_TARGET_4_RANGE to false,
        DataType.Field.RADAR_TARGET_5_RANGE to false,
        DataType.Field.RADAR_TARGET_6_RANGE to false,
        DataType.Field.RADAR_TARGET_7_RANGE to false,
        DataType.Field.RADAR_TARGET_8_RANGE to false
    )
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
            val prefs = applicationContext.streamSettings()
            val rideStateFlow = karooSystem.streamRideState()

            karooSystem.streamDataFlow(DataType.Type.RADAR)
                .mapNotNull { (it as? StreamState.Streaming)?.dataPoint?.values }
                .combine(rideStateFlow) { values, rideState ->
                    values to rideState
                }
                .combine(prefs) { (values, rideState), settings ->
                    Triple(values, rideState, settings)
                }
                .collect({ (values, rideState, settings) ->
                    val threatLevel = values[DataType.Field.RADAR_THREAT_LEVEL] ?: 0.0
                    if (settings.enabled &&
                        ((settings.inRideOnly && rideState is RideState.Recording) || !settings.inRideOnly)
                    ) {
                        if (!radarThreat && threatLevel > 0) {
                            Log.i(TAG, "Threar")
                            karooSystem.beep(settings.threatLevelFreq, settings.threatLevelDur)
                        }
                        if (radarThreat && threatLevel == 0.0) {
                            Log.i(TAG, "Clear")
                            karooSystem.beep(settings.threaPassedtLevelFreq, settings.threatPassedLevelDur)
                        }
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
