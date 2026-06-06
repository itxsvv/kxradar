package org.itxsvv.kxradar

import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.SavedDevices
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.TurnScreenOn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.itxsvv.kxradar.lightcontrol.KarooLightControl
import org.itxsvv.kxradar.lightcontrol.LightMode

class KarooRadarExtension : KarooExtension("kxradar", "1.0.7") {
    companion object {
        const val TAG = "kxradar"
        private const val ALL_CLEAR_DELAY_MS = 2_000L
        private const val BIKE_LIGHT_DATA_TYPE = "TYPE_BIKE_LIGHT_ID"
        private const val DEVICE_TYPE_BIKE_LIGHT = 35
    }

    private lateinit var karooSystem: KarooSystemService
    private var serviceJob: Job? = null
    private var radarThreat = false
    private var allClearStartedTime = 0L
    private var wasRidePaused = false
    internal lateinit var lightControl: KarooLightControl
    @Volatile private var rearLightId: String? = null
    private var savedDevicesConsumerId: String? = null
    private val extensionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Radar extension initialized")
        karooSystem = KarooSystemService(applicationContext)
        lightControl = KarooLightControl(applicationContext)
        serviceJob = CoroutineScope(Dispatchers.IO).launch {
            connectToKarooSystem()
            startMonitoring()
        }
    }

    override fun onDestroy() {
        serviceJob?.cancel()
        serviceJob = null
        lightControl.unbind()
        karooSystem.disconnect()
        extensionScope.cancel()
        super.onDestroy()
    }

    private fun connectToKarooSystem() {
        karooSystem.connect { connected ->
            if (connected) {
                Log.i(TAG, "karooSystem Connected")
                lightControl.bind()
                discoverKarooLights()
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
        if (!settings.enabled) {
            allClearStartedTime = 0L
            radarThreat = threatLevel != 0.0
            wasRidePaused = rideState is RideState.Paused
            return
        }

        if (radarThreat && threatLevel == 0.0) {
            allClearStartedTime = System.currentTimeMillis()
        }

        val shouldHandleAllClear = shouldTriggerAllClear()

        handleLightState(threatLevel, rideState, settings, shouldHandleAllClear)
        if (settings.enabled) {
            if (!radarThreat && threatLevel > 0) {
                handleThreatDetected(threatLevel, settings, rideState)
            }
            handleSoundAllClearIfNeeded(settings, rideState, shouldHandleAllClear)
        }

        if (shouldHandleAllClear) {
            allClearStartedTime = 0L
        }

        radarThreat = threatLevel != 0.0
        wasRidePaused = rideState is RideState.Paused
    }

    private fun handleLightState(
        threatLevel: Double,
        rideState: RideState,
        settings: RadarSettings,
        shouldHandleAllClear: Boolean,
    ) {
        val isRidePaused = rideState is RideState.Paused

        if (!settings.lightControlEnabled) {
            if (radarThreat) {
                forceLightOff()
            }
            return
        }

        if (!wasRidePaused && isRidePaused) {
            forceLightOff()
            return
        }

        if (!radarThreat && threatLevel > 0 && !isRidePaused) {
            light(true, settings)
            allClearStartedTime = 0L
        }

        if (threatLevel == 0.0 && shouldHandleAllClear) {
            handleLightAllClear(settings)
        }
    }

    private fun handleThreatDetected(
        threatLevel: Double,
        settings: RadarSettings,
        rideState: RideState
    ) {
        Log.i(TAG, "Threat detected")
        allClearStartedTime = 0L
        if (settings.wakeUpScreen) {
            karooSystem.dispatch(TurnScreenOn)
        }
        val beepCount = if (settings.redThreadAlert && threatLevel > 1.0) 2 else 1
        if(isHandleThreatAllowed(settings, rideState)) {
            karooSystem.beep(
                settings.threatBeep.frequency,
                settings.threatBeep.duration,
                beepCount,
            )
        }
    }

    private fun handleLightAllClear(settings: RadarSettings) {
        Log.i(TAG, "All-clear")
        light(false, settings)
    }

    private fun handleSoundAllClearIfNeeded(
        settings: RadarSettings,
        rideState: RideState,
        shouldTriggerAllClear: Boolean,
    ) {
        if (!shouldTriggerAllClear) {
            return
        }
        if(isHandleThreatAllowed(settings, rideState)) {
            karooSystem.beep(
                settings.passedBeep.frequency,
                settings.passedBeep.duration,
            )
        }
    }

    private fun isHandleThreatAllowed(
        settings: RadarSettings,
        rideState: RideState
    ): Boolean = ((settings.inRideOnly && rideState is RideState.Recording) || !settings.inRideOnly)

    private fun shouldTriggerAllClear(): Boolean {
        return allClearStartedTime > 0 &&
            System.currentTimeMillis() - allClearStartedTime > ALL_CLEAR_DELAY_MS
    }

    fun light(on: Boolean, settings: RadarSettings) {
        if(!settings.lightControlEnabled) {
            return
        }
        if (on) {
            rearLightId?.let { lightControl.setLightMode(it, settings.lightControlMode.karooName) }
        } else {
            rearLightId?.let { lightControl.setLightMode(it, LightMode.OFF.karooName) }
        }
    }

    private fun forceLightOff() {
        rearLightId?.let { lightControl.setLightMode(it, LightMode.OFF.karooName) }
    }

    internal fun discoverKarooLights() {
        savedDevicesConsumerId?.let { karooSystem.removeConsumer(it) }
        extensionScope.launch {
            Log.d(TAG, "Querying Karoo for saved bike light devices")
            savedDevicesConsumerId = karooSystem.addConsumer<SavedDevices> { savedDevices ->
                val lights = savedDevices.devices.filter { device ->
                    device.supportedDataTypes.contains(BIKE_LIGHT_DATA_TYPE) && device.enabled
                }
                Log.d(TAG, "Found ${lights.size} saved bike light(s)")
                for (device in lights) {
                    val parts = device.id.split("-")
                    if (parts.size >= 3) {
                        val deviceType = parts[1].toIntOrNull()
                        if (deviceType == DEVICE_TYPE_BIKE_LIGHT) {
                            if (rearLightId == null) {
                                rearLightId = device.id
                                Log.d(TAG,"Rear light: ${device.name} (${device.id})")
                            }
                        }
                    }
                }
            }
        }
    }

}
