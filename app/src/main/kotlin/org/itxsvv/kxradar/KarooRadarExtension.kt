package org.itxsvv.kxradar

import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.models.DataType
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.itxsvv.kxradar.lightcontrol.KarooLightControl
import org.itxsvv.kxradar.lightcontrol.LightMode

class KarooRadarExtension : KarooExtension("kxradar", "1.0.8") {
    companion object {
        const val TAG = "kxradar"
        internal const val ALL_CLEAR_DELAY_MS = 2_000L
        private const val SUN_CHECK_INTERVAL_MS = 5 * 60 * 1000L
        private const val BIKE_LIGHT_DATA_TYPE = "TYPE_BIKE_LIGHT_ID"
        private const val DEVICE_TYPE_BIKE_LIGHT = 35
    }

    private lateinit var karooSystem: KarooSystemService
    private var serviceJob: Job? = null
    @Volatile private var latestSettings = RadarSettings()
    internal lateinit var lightControl: KarooLightControl
    @Volatile private var rearLightId: String? = null
    private var savedDevicesConsumerId: String? = null
    private val extensionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sunTimeNormalizer = SunTimeNormalizer()
    private val alertController = RadarAlertController(object : RadarAlertEffects {
        override fun wakeScreen() {
            karooSystem.dispatch(TurnScreenOn)
        }

        override fun playThreatBeep(frequency: Int, duration: Int, count: Int) {
            karooSystem.beep(frequency, duration, count)
        }

        override fun playAllClearBeep(frequency: Int, duration: Int) {
            karooSystem.beep(frequency, duration)
        }

        override fun setLightMode(mode: LightMode): Boolean {
            val lightId = rearLightId ?: return false
            return lightControl.setLightMode(lightId, mode.karooName)
        }

        override fun logThreatDetected() {
            Log.i(TAG, "Threat detected")
        }

        override fun logAllClear() {
            Log.i(TAG, "All-clear")
        }
    })

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Radar extension initialized")
        karooSystem = KarooSystemService(applicationContext)
        lightControl = KarooLightControl(applicationContext) { connected ->
            if (connected) {
                alertController.onLightAvailable(latestSettings)
            } else {
                alertController.onLightUnavailable()
            }
        }
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
                latestSettings = settings
                alertController.onRadarUpdate(
                    threatLevel = values[DataType.Field.RADAR_THREAT_LEVEL] ?: 0.0,
                    rideState = rideState,
                    settings = settings,
                )
            }
    }

    private fun startSunAutomation() {
        extensionScope.launch {
            RadarSettingsService(applicationContext).settings.collect { settings ->
                latestSettings = settings
                alertController.onSunTimerTick(settings)
            }
        }
        extensionScope.launch {
            observeSunTime(DataType.Type.SUNRISE, DataType.Field.SUNRISE) { sunrise ->
                alertController.onSunriseUpdated(sunrise, latestSettings)
            }
        }
        extensionScope.launch {
            observeSunTime(DataType.Type.SUNSET, DataType.Field.SUNSET) { sunset ->
                alertController.onSunsetUpdated(sunset, latestSettings)
            }
        }
        extensionScope.launch {
            while (isActive) {
                alertController.onSunTimerTick(latestSettings)
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
            .mapNotNull { (it as? StreamState.Streaming)?.dataPoint?.values?.get(fieldId) }
            .distinctUntilChanged()
            .mapNotNull { value ->
                val normalized = sunTimeNormalizer.toEpochMillis(value, System.currentTimeMillis())
                if (normalized == null) {
                    Log.w(TAG, "$fieldId has unsupported time value: $value")
                } else {
                    Log.d(TAG, "$fieldId raw=$value normalized=$normalized")
                }
                normalized
            }
            .collect { onUpdate(it) }
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
                val rearLight = lights.firstOrNull { device ->
                    val parts = device.id.split("-")
                    parts.size >= 3 && parts[1].toIntOrNull() == DEVICE_TYPE_BIKE_LIGHT
                }
                val previousLightId = rearLightId
                rearLightId = rearLight?.id
                if (previousLightId != rearLightId) {
                    alertController.onLightUnavailable()
                    rearLight?.let {
                        Log.d(TAG, "Rear light: ${it.name} (${it.id})")
                        alertController.onLightAvailable(latestSettings)
                    }
                }
            }
        }
    }

}
