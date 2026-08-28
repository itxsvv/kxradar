package org.itxsvv.kxradar

import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnLocationChanged
import io.hammerhead.karooext.models.SavedDevices
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.TurnScreenOn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.itxsvv.kxradar.lightcontrol.KarooLightControl
import org.itxsvv.kxradar.lightcontrol.LightMode
import java.io.IOException
import java.time.Instant

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
    private var locationConsumerId: String? = null
    @Volatile private var locationStreamJob: Job? = null
    private val extensionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sunAutomationCoordinator = SunAutomationCoordinator()
    private lateinit var sunLocationRepository: SunLocationRepository
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
            val lightId = rearLightId
            if (lightId == null) {
                Log.w(TAG, "Light command skipped: requested=$mode, rear light is unavailable")
                return false
            }
            val success = lightControl.setLightMode(lightId, mode.karooName)
            val message = "Light command: requested=$mode, lightId=$lightId, success=$success"
            if (success) {
                Log.i(TAG, message)
            } else {
                Log.w(TAG, message)
            }
            return success
        }

        override fun logThreatDetected() {
            Log.i(TAG, "Threat detected")
        }

        override fun logAllClear() {
            Log.i(TAG, "All-clear")
        }

        override fun logLightControl(message: String) {
            Log.i(TAG, message)
        }
    })

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Radar extension initialized")
        karooSystem = KarooSystemService(applicationContext)
        sunLocationRepository = SunLocationRepository(applicationContext)
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
        locationConsumerId?.let { karooSystem.removeConsumer(it) }
        locationStreamJob?.cancel()
        locationStreamJob = null
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
        observeSunSettings()
        restoreSunLocation()
        observeKarooLocationEvents()
        observeKarooLocationDataType()
        startSunTimer()
    }

    private fun observeSunSettings() {
        extensionScope.launch {
            RadarSettingsService(applicationContext).settings.collect { settings ->
                latestSettings = settings
                alertController.onSunTimerTick(settings)
            }
        }
    }

    private fun restoreSunLocation() {
        extensionScope.launch {
            val cachedLocation = sunLocationRepository.location.first()
            if (cachedLocation == null) {
                Log.i(TAG, "No cached sun location; waiting for Karoo location")
            } else {
                Log.i(TAG, "Restoring cached sun location")
                stopLocationDataTypeFallback("cached location restore")
                applySunAutomationUpdate(
                    sunAutomationCoordinator.restoreLocation(cachedLocation),
                    source = "cache",
                )
            }
        }
    }

    private fun observeKarooLocationEvents() {
        locationConsumerId = karooSystem.addConsumer<OnLocationChanged> { event ->
            extensionScope.launch {
                handleSunLocation(
                    location = SunLocation(event.lat, event.lng),
                    source = "location event",
                )
            }
        }
    }

    private fun observeKarooLocationDataType() {
        locationStreamJob = extensionScope.launch {
            Log.i(TAG, "Subscribing to Karoo location data type")
            val location = karooSystem.streamDataFlow(DataType.Type.LOCATION)
                .catch { error -> Log.e(TAG, "Karoo location stream failed", error) }
                .mapNotNull(::locationFromStreamState)
                .firstOrNull()
            locationStreamJob = null
            if (location == null) {
                Log.w(TAG, "Karoo location data type ended without a valid location")
                return@launch
            }
            Log.i(TAG, "Karoo location data type fallback received a valid location")
            handleSunLocation(location, source = "location data type")
        }
    }

    private fun locationFromStreamState(state: StreamState): SunLocation? {
        if (state !is StreamState.Streaming) {
            Log.i(TAG, "Karoo location stream state=$state")
            return null
        }
        val values = state.dataPoint.values
        val latitude = values[DataType.Field.LOC_LATITUDE]
        val longitude = values[DataType.Field.LOC_LONGITUDE]
        if (latitude == null || longitude == null) {
            Log.w(TAG, "Karoo location data missing coordinates: fields=${values.keys}")
            return null
        }
        val location = SunLocation(latitude, longitude)
        if (!location.isValid()) {
            Log.w(TAG, "Ignoring invalid Karoo location from location data type")
            return null
        }
        return location
    }

    private suspend fun handleSunLocation(location: SunLocation, source: String) {
        if (!location.isValid()) {
            Log.w(TAG, "Ignoring invalid Karoo location from $source")
            return
        }
        stopLocationDataTypeFallback(source)
        applySunAutomationUpdate(
            sunAutomationCoordinator.onLocationUpdate(location),
            source = source,
        )
    }

    private fun stopLocationDataTypeFallback(source: String) {
        val streamJob = locationStreamJob ?: return
        locationStreamJob = null
        streamJob.cancel()
        Log.i(TAG, "Stopped Karoo location data type fallback after $source")
    }

    private fun startSunTimer() {
        extensionScope.launch {
            while (isActive) {
                applySunAutomationUpdate(
                    sunAutomationCoordinator.refresh(),
                    source = "date refresh",
                )
                alertController.onSunTimerTick(latestSettings)
                delay(SUN_CHECK_INTERVAL_MS)
            }
        }
    }

    private suspend fun applySunAutomationUpdate(
        update: SunAutomationUpdate,
        source: String,
    ) {
        update.sunTimes?.let { sunTimes ->
            Log.i(
                TAG,
                "Sun times calculated from $source: " +
                    "sunrise=${Instant.ofEpochMilli(sunTimes.sunriseTime)}, " +
                    "sunset=${Instant.ofEpochMilli(sunTimes.sunsetTime)}",
            )
            alertController.onSunTimesUpdated(sunTimes, latestSettings)
        }
        if (update.calculationAttempted && update.sunTimes == null) {
            Log.w(TAG, "Sun time calculation failed for $source")
        }
        update.locationToPersist?.let { location ->
            try {
                sunLocationRepository.save(location)
                Log.i(TAG, "Saved sun location from $source")
            } catch (error: IOException) {
                Log.e(TAG, "Failed to save sun location from $source", error)
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
