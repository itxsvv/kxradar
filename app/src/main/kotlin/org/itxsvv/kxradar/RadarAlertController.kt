package org.itxsvv.kxradar

import io.hammerhead.karooext.models.RideState
import org.itxsvv.kxradar.lightcontrol.LightMode

internal interface RadarAlertEffects {
    fun wakeScreen()
    fun playThreatBeep(frequency: Int, duration: Int, count: Int)
    fun playAllClearBeep(frequency: Int, duration: Int)
    fun setLightMode(mode: LightMode)
    fun forceLightOff()
    fun logThreatDetected()
    fun logAllClear()
}

internal data class RadarAlertState(
    val radarThreat: Boolean = false,
    val radarLightRequested: Boolean = false,
    val sunLightRequested: Boolean = false,
    val appliedLightState: Boolean = false,
    val allClearStartedTime: Long = 0L,
    val wasRidePaused: Boolean = false,
    val isRidePaused: Boolean = false,
    val latestSunriseTime: Long = 0L,
    val latestSunsetTime: Long = 0L,
)

internal class RadarAlertController(
    private val effects: RadarAlertEffects,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sunLightPolicy: SunLightPolicy = SunLightPolicy(),
) {
    private var state = RadarAlertState()

    fun onRadarUpdate(
        threatLevel: Double,
        rideState: RideState,
        settings: RadarSettings,
    ) {
        state = state.copy(isRidePaused = rideState is RideState.Paused)

        if (!settings.enabled) {
            state = state.copy(
                allClearStartedTime = 0L,
                radarThreat = threatLevel != 0.0,
                wasRidePaused = state.isRidePaused,
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

        state = state.copy(
            radarThreat = threatLevel != 0.0,
            wasRidePaused = state.isRidePaused,
        )
    }

    fun onSunriseUpdated(sunrise: Long, settings: RadarSettings) {
        state = state.copy(latestSunriseTime = sunrise)
        evaluateSunLightRequest(settings)
    }

    fun onSunsetUpdated(sunset: Long, settings: RadarSettings) {
        state = state.copy(latestSunsetTime = sunset)
        evaluateSunLightRequest(settings)
    }

    fun onSunTimerTick(settings: RadarSettings) {
        evaluateSunLightRequest(settings)
    }

    private fun handleLightState(
        threatLevel: Double,
        settings: RadarSettings,
        shouldHandleAllClear: Boolean,
    ) {
        if (!settings.lightControlEnabled) {
            if (state.appliedLightState) {
                effects.forceLightOff()
            }
            state = state.copy(
                appliedLightState = false,
                radarLightRequested = false,
                sunLightRequested = false,
            )
            return
        }

        if (!state.wasRidePaused && state.isRidePaused) {
            updateLightState(settings)
        }

        if (state.wasRidePaused && !state.isRidePaused) {
            evaluateSunLightRequest(settings)
            updateLightState(settings)
        }

        if (!state.radarThreat && threatLevel > 0 && !state.isRidePaused) {
            state = state.copy(
                radarLightRequested = true,
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
            if (state.appliedLightState) {
                effects.forceLightOff()
            }
            state = state.copy(
                sunLightRequested = false,
                radarLightRequested = false,
                appliedLightState = false,
            )
            return
        }
        if (!settings.lightAutoBySunEnabled) {
            if (state.sunLightRequested) {
                state = state.copy(sunLightRequested = false)
                updateLightState(settings)
            }
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

        if (state.sunLightRequested != sunWindowActive) {
            state = state.copy(sunLightRequested = sunWindowActive)
            updateLightState(settings)
        }
    }

    private fun updateLightState(settings: RadarSettings) {
        if (!settings.enabled || !settings.lightControlEnabled) {
            return
        }
        val shouldBeOn = !state.isRidePaused && (state.radarLightRequested || state.sunLightRequested)
        if (shouldBeOn == state.appliedLightState) {
            return
        }
        if (shouldBeOn) {
            effects.setLightMode(settings.lightControlMode)
        } else {
            effects.forceLightOff()
        }
        state = state.copy(appliedLightState = shouldBeOn)
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
