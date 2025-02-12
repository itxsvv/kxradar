package org.itxsvv.kxradar

import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

class KarooRadarExtension : KarooExtension("kxradar", "1.0.3") {
    companion object {
        const val TAG = "kxradar"
    }
    private var DELAY_BEEP_ALL_CLEAR = 2000
    private lateinit var karooSystem: KarooSystemService
    private var serviceJob: Job? = null
    private var radarThreat = false
    private var passedDelay = 0L

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
                            Log.i(TAG, "Threat detected")
                            passedDelay = 0
                            karooSystem.beep(settings.threatBeep.frequency, settings.threatBeep.duration)
                        }
                        if(passedDelay > 0 && System.currentTimeMillis() - passedDelay > DELAY_BEEP_ALL_CLEAR) {
                            Log.i(TAG, "All-clear")
                            passedDelay = 0;
                            karooSystem.beep(settings.passedBeep.frequency, settings.passedBeep.duration)
                        }
                        if (radarThreat && threatLevel == 0.0) {
                            passedDelay = System.currentTimeMillis()
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
