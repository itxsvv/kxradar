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

class KarooRadarExtension : KarooExtension("kxradar", "1.0.6") {
    companion object {
        const val TAG = "kxradar"
        private const val ALL_CLEAR_DELAY_MS = 2_000L
        private const val BIKE_LIGHT_DATA_TYPE = "TYPE_BIKE_LIGHT_ID"
        private const val DEVICE_TYPE_BIKE_LIGHT = 35
    }

    private lateinit var karooSystem: KarooSystemService
    private var serviceJob: Job? = null
    private var radarThreat = false
    private var passedDelay = 0L
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
        light(true, settings)
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
        light(false, settings)
        karooSystem.beep(
            settings.passedBeep.frequency,
            settings.passedBeep.duration,
        )
    }

    fun light(on: Boolean, settings: RadarSettings) {
        if(settings.lightControlEnabled) {
            if (on) {
                rearLightId?.let { lightControl.setLightMode(it, settings.lightControlMode.karooName) }
            } else {
                rearLightId?.let { lightControl.setLightMode(it, LightMode.OFF.karooName) }
            }
        }
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
