package org.itxsvv.kxradar

import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.TurnScreenOn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

class KarooRadarExtension : KarooExtension("kxradar", "1.0.5") {
    companion object {
        const val TAG = "kxradar"
        private const val ALL_CLEAR_DELAY_MS = 2_000L
    }

    private lateinit var karooSystem: KarooSystemService
    private var serviceJob: Job? = null
    private var radarThreat = false
    private var passedDelay = 0L

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Radar extension initialized")
        karooSystem = KarooSystemService(applicationContext)
        serviceJob = CoroutineScope(Dispatchers.IO).launch {
            connectToKarooSystem()
            startMonitoring()
        }
    }

    override fun onDestroy() {
        serviceJob?.cancel()
        serviceJob = null
        karooSystem.disconnect()
        super.onDestroy()
    }

    private suspend fun connectToKarooSystem() {
        karooSystem.connect { connected ->
            if (connected) {
                Log.i(TAG, "karooSystem Connected")
            }
        }
    }

    private suspend fun startMonitoring() {
        val settingsFlow = RadarSettingsService(applicationContext).settings
        val rideStateFlow = karooSystem.streamRideState()

        karooSystem.streamDataFlow(DataType.Type.RADAR)
            .mapNotNull { (it as? StreamState.Streaming)?.dataPoint?.values }
            .combine(rideStateFlow) { values, rideState ->
                values to rideState
            }
            .combine(settingsFlow) { (values, rideState), settings ->
                Triple(values, rideState, settings)
            }
            .collect { (values, rideState, settings) ->
                handleRadarUpdate(
                    threatLevel = values[DataType.Field.RADAR_THREAT_LEVEL] ?: 0.0,
                    rideState = rideState,
                    settings = settings,
                )
            }
    }

    private fun handleRadarUpdate(
        threatLevel: Double,
        rideState: RideState,
        settings: RadarSettings,
    ) {
        if (settings.enabled &&
            ((settings.inRideOnly && rideState is RideState.Recording) || !settings.inRideOnly)
        ) {
            if (!radarThreat && threatLevel > 0) {
                handleThreatDetected(threatLevel, settings)
            }
            handleAllClearIfNeeded(settings)
            if (radarThreat && threatLevel == 0.0) {
                passedDelay = System.currentTimeMillis()
            }
        }
        radarThreat = threatLevel != 0.0
    }

    private fun handleThreatDetected(
        threatLevel: Double,
        settings: RadarSettings,
    ) {
        Log.i(TAG, "Threat detected")
        passedDelay = 0
        if (settings.wakeUpScreen) {
            karooSystem.dispatch(TurnScreenOn)
        }
        val beepCount = if (settings.redThreadAlert && threatLevel > 1.0) 2 else 1
        karooSystem.beep(
            settings.threatBeep.frequency,
            settings.threatBeep.duration,
            beepCount,
        )
    }

    private fun handleAllClearIfNeeded(settings: RadarSettings) {
        if (passedDelay <= 0 || System.currentTimeMillis() - passedDelay <= ALL_CLEAR_DELAY_MS) {
            return
        }
        Log.i(TAG, "All-clear")
        passedDelay = 0
        karooSystem.beep(
            settings.passedBeep.frequency,
            settings.passedBeep.duration,
        )
    }
}
