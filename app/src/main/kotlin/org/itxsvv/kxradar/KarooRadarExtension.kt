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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.itxsvv.kxradar.lightcontrol.KarooLightControl
import org.itxsvv.kxradar.lightcontrol.LightMode

class KarooRadarExtension : KarooExtension("kxradar", "1.0.7") {
    companion object {
        const val TAG = "kxradar"
        private const val ALL_CLEAR_DELAY_MS = 2_000L
        private const val SUN_CHECK_INTERVAL_MS = 10 * 60 * 1000L
        private const val BIKE_LIGHT_DATA_TYPE = "TYPE_BIKE_LIGHT_ID"
        private const val DEVICE_TYPE_BIKE_LIGHT = 35
    }

    private lateinit var karooSystem: KarooSystemService
    private var serviceJob: Job? = null
    private var radarThreat = false
    private var radarLightRequested = false
    private var sunLightRequested = false
    private var appliedLightState = false
    private var allClearStartedTime = 0L
    private var wasRidePaused = false
    private var isRidePaused = false
    private var latestSunriseTime = 0L
    private var latestSunsetTime = 0L
    private var latestSettings = RadarSettings()
    internal lateinit var lightControl: KarooLightControl
    @Volatile private var rearLightId: String? = null
    private var savedDevicesConsumerId: String? = null
    private val extensionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Radar extension initialized")
        karooSystem = KarooSystemService(applicationContext)
        lightControl = KarooLightControl(applicationContext)
        startSunAutomation()
        serviceJob = CoroutineScope(Dispatchers.IO).launch {
            connectToKarooSystem()
            startMonitoring()
        }
    }

    override fun onDestroy() {
        serviceJob?.cancel()
        serviceJob = null
        lightControl.unbind()
        savedDevicesConsumerId?.let { karooSystem.removeConsumer(it) }
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
        latestSettings = settings
        isRidePaused = rideState is RideState.Paused

        if (!settings.enabled) {
            allClearStartedTime = 0L
            radarThreat = threatLevel != 0.0
            wasRidePaused = isRidePaused
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
            radarLightRequested = false
            allClearStartedTime = 0L
        }

        radarThreat = threatLevel != 0.0
        wasRidePaused = isRidePaused
    }

    private fun handleLightState(
        threatLevel: Double,
        rideState: RideState,
        settings: RadarSettings,
        shouldHandleAllClear: Boolean,
    ) {
        if (!settings.lightControlEnabled) {
            if (appliedLightState) {
                forceLightOff()
                appliedLightState = false
            }
            radarLightRequested = false
            sunLightRequested = false
            return
        }

        if (!wasRidePaused && isRidePaused) {
            updateLightState(settings)
        }

        if (wasRidePaused && !isRidePaused) {
            evaluateSunLightRequest(settings)
            updateLightState(settings)
        }

        if (!radarThreat && threatLevel > 0 && !isRidePaused) {
            radarLightRequested = true
            allClearStartedTime = 0L
            updateLightState(settings)
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
        radarLightRequested = false
        updateLightState(settings)
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

    private fun startSunAutomation() {
        extensionScope.launch {
            RadarSettingsService(applicationContext).settings.collect { settings ->
                latestSettings = settings
                evaluateSunLightRequest(settings)
            }
        }
        extensionScope.launch {
            observeSunTime(DataType.Type.SUNRISE, DataType.Field.SUNRISE) { sunrise ->
                latestSunriseTime = sunrise
                evaluateSunLightRequest(latestSettings)
            }
        }
        extensionScope.launch {
            observeSunTime(DataType.Type.SUNSET, DataType.Field.SUNSET) { sunset ->
                latestSunsetTime = sunset
                evaluateSunLightRequest(latestSettings)
            }
        }
        extensionScope.launch {
            while (isActive) {
                evaluateSunLightRequest(latestSettings)
                delay(SUN_CHECK_INTERVAL_MS)
            }
        }
    }

    private suspend fun observeSunTime(
        dataTypeId: String,
        fieldId: String,
        onUpdate: (Long) -> Unit,
    ) {
        karooSystem.streamDataFlow(dataTypeId)
            .mapNotNull { (it as? StreamState.Streaming)?.dataPoint?.values?.get(fieldId)?.toLong() }
            .collect { onUpdate(it) }
    }

    private fun evaluateSunLightRequest(settings: RadarSettings) {
        if (!settings.enabled) {
            return
        }
        if (!settings.lightControlEnabled) {
            sunLightRequested = false
            radarLightRequested = false
            if (appliedLightState) {
                forceLightOff()
                appliedLightState = false
            }
            return
        }
        if (!settings.lightAutoBySunEnabled) {
            if (sunLightRequested) {
                sunLightRequested = false
                updateLightState(settings)
            }
            return
        }
        if (latestSunriseTime <= 0L || latestSunsetTime <= 0L) {
            return
        }

        val sunWindowActive = isSunWindowActive(
            now = System.currentTimeMillis(),
            sunriseTime = latestSunriseTime,
            sunsetTime = latestSunsetTime,
            sunriseOffsetMinutes = settings.lightSunriseOffsetMinutes,
            sunsetOffsetMinutes = settings.lightSunsetOffsetMinutes,
        )

        if (sunLightRequested != sunWindowActive) {
            sunLightRequested = sunWindowActive
            updateLightState(settings)
        }
    }

    private fun isSunWindowActive(
        now: Long,
        sunriseTime: Long,
        sunsetTime: Long,
        sunriseOffsetMinutes: Int,
        sunsetOffsetMinutes: Int,
    ): Boolean {
        val sunriseBoundary = sunriseTime + sunriseOffsetMinutes * 60_000L
        val sunsetBoundary = sunsetTime + sunsetOffsetMinutes * 60_000L
        return now >= sunsetBoundary || now < sunriseBoundary
    }

    private fun updateLightState(settings: RadarSettings) {
        if (!settings.enabled || !settings.lightControlEnabled) {
            return
        }
        val shouldBeOn = !isRidePaused && (radarLightRequested || sunLightRequested)
        if (shouldBeOn == appliedLightState) {
            return
        }
        if (shouldBeOn) {
            light(true, settings)
        } else {
            forceLightOff()
        }
        appliedLightState = shouldBeOn
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
