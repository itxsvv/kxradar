package org.itxsvv.kxradar

import io.hammerhead.karooext.models.RideState
import org.itxsvv.kxradar.lightcontrol.LightMode

internal interface RadarAlertEffects {
    fun wakeScreen()
    fun playThreatBeep(frequency: Int, duration: Int, count: Int)
    fun playAllClearBeep(frequency: Int, duration: Int)
    fun setLightMode(mode: LightMode): Boolean
    fun logThreatDetected()
    fun logAllClear()
}

internal data class RadarAlertState(
    val radarThreat: Boolean = false,
    val radarLightRequested: Boolean = false,
    val sunLightRequested: Boolean = false,
    val appliedLightMode: LightMode? = null,
    val lightControlActive: Boolean = false,
    val allClearStartedTime: Long = 0L,
    val latestSunriseTime: Long = 0L,
    val latestSunsetTime: Long = 0L,
)

internal class RadarAlertController(
    private val effects: RadarAlertEffects,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sunLightPolicy: SunLightPolicy = SunLightPolicy(),
) {
    private var state = RadarAlertState()

    @Synchronized
    fun onRadarUpdate(
        threatLevel: Double,
        rideState: RideState,
        settings: RadarSettings,
    ) {
        if (!settings.enabled) {
            state = state.copy(
                allClearStartedTime = 0L,
                radarThreat = threatLevel != 0.0,
            )
            return
        }
        if (state.radarThreat && threatLevel == 0.0) {
            state = state.copy(allClearStartedTime = clock())
        }
        val shouldHandleAllClear = shouldTriggerAllClear()
        handleLightState(threatLevel, settings, shouldHandleAllClear)
        if (!state.radarThreat && threatLevel > 0) {
            handleThreatDetected(threatLevel, settings, rideState)
        }
        handleSoundAllClearIfNeeded(settings, rideState, shouldHandleAllClear)
        if (shouldHandleAllClear) {
            state = state.copy(
                radarLightRequested = false,
                allClearStartedTime = 0L,
            )
        }
        state = state.copy(radarThreat = threatLevel != 0.0)
    }

    @Synchronized
    fun onSunriseUpdated(sunrise: Long, settings: RadarSettings) {
        state = state.copy(latestSunriseTime = sunrise)
        evaluateSunLightRequest(settings)
    }

    @Synchronized
    fun onSunsetUpdated(sunset: Long, settings: RadarSettings) {
        state = state.copy(latestSunsetTime = sunset)
        evaluateSunLightRequest(settings)
    }

    @Synchronized
    fun onSunTimerTick(settings: RadarSettings) {
        evaluateSunLightRequest(settings)
    }

    @Synchronized
    fun onLightAvailable(settings: RadarSettings) {
        state = state.copy(appliedLightMode = null)
        evaluateSunLightRequest(settings)
        updateLightState(settings)
    }

    @Synchronized
    fun onLightUnavailable() {
        state = state.copy(appliedLightMode = null)
    }

    private fun handleLightState(
        threatLevel: Double,
        settings: RadarSettings,
        shouldHandleAllClear: Boolean,
    ) {
        if (!settings.lightControlEnabled) {
            disableLightControl()
            return
        }
        if (!state.radarThreat && threatLevel > 0) {
            state = state.copy(
                radarLightRequested = true,
                lightControlActive = true,
                allClearStartedTime = 0L,
            )
            updateLightState(settings)
        }
        if (threatLevel == 0.0 && shouldHandleAllClear) {
            handleLightAllClear(settings)
        }
    }

    private fun handleThreatDetected(
        threatLevel: Double,
        settings: RadarSettings,
        rideState: RideState,
    ) {
        effects.logThreatDetected()
        state = state.copy(allClearStartedTime = 0L)
        if (settings.wakeUpScreen) {
            effects.wakeScreen()
        }
        val beepCount = if (settings.redThreadAlert && threatLevel > 1.0) 2 else 1
        if (isHandleThreatAllowed(settings, rideState)) {
            effects.playThreatBeep(
                settings.threatBeep.frequency,
                settings.threatBeep.duration,
                beepCount,
            )
        }
    }

    private fun handleLightAllClear(settings: RadarSettings) {
        effects.logAllClear()
        state = state.copy(radarLightRequested = false)
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
        if (isHandleThreatAllowed(settings, rideState)) {
            effects.playAllClearBeep(
                settings.passedBeep.frequency,
                settings.passedBeep.duration,
            )
        }
    }

    private fun evaluateSunLightRequest(settings: RadarSettings) {
        if (!settings.enabled) {
            return
        }
        if (!settings.lightControlEnabled) {
            disableLightControl()
            return
        }
        if (!settings.lightAutoBySunEnabled) {
            state = state.copy(sunLightRequested = false)
            updateLightState(settings)
            return
        }
        if (state.latestSunriseTime <= 0L || state.latestSunsetTime <= 0L) {
            return
        }
        val sunWindowActive = sunLightPolicy.isSunWindowActive(
            now = clock(),
            sunriseTime = state.latestSunriseTime,
            sunsetTime = state.latestSunsetTime,
            sunriseOffsetMinutes = settings.lightSunriseOffsetMinutes,
            sunsetOffsetMinutes = settings.lightSunsetOffsetMinutes,
        )
        state = state.copy(
            sunLightRequested = sunWindowActive,
            lightControlActive = true,
        )
        updateLightState(settings)
    }

    private fun updateLightState(settings: RadarSettings) {
        if (!settings.enabled || !settings.lightControlEnabled || !state.lightControlActive) {
            return
        }
        val requestedMode = if (state.radarLightRequested || state.sunLightRequested) {
            settings.lightControlMode
        } else {
            LightMode.OFF
        }
        if (requestedMode == state.appliedLightMode) {
            return
        }
        if (effects.setLightMode(requestedMode)) {
            state = state.copy(
                appliedLightMode = requestedMode,
                lightControlActive = requestedMode != LightMode.OFF || settings.lightAutoBySunEnabled,
            )
        }
    }

    private fun disableLightControl() {
        val shouldTurnOff = state.lightControlActive ||
            (state.appliedLightMode != null && state.appliedLightMode != LightMode.OFF)
        if (!shouldTurnOff) {
            state = state.copy(
                radarLightRequested = false,
                sunLightRequested = false,
                lightControlActive = false,
            )
            return
        }
        val lightTurnedOff = state.appliedLightMode == LightMode.OFF || effects.setLightMode(LightMode.OFF)
        state = state.copy(
            radarLightRequested = false,
            sunLightRequested = false,
            appliedLightMode = if (lightTurnedOff) LightMode.OFF else state.appliedLightMode,
            lightControlActive = !lightTurnedOff,
        )
    }

    private fun shouldTriggerAllClear(): Boolean {
        return state.allClearStartedTime > 0 &&
            clock() - state.allClearStartedTime > KarooRadarExtension.ALL_CLEAR_DELAY_MS
    }

    private fun isHandleThreatAllowed(
        settings: RadarSettings,
        rideState: RideState,
    ): Boolean = ((settings.inRideOnly && rideState is RideState.Recording) || !settings.inRideOnly)
}
