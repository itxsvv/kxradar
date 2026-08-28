package org.itxsvv.kxradar

import io.hammerhead.karooext.models.RideState
import java.time.Instant
import org.itxsvv.kxradar.lightcontrol.LightMode
import org.junit.Assert.assertEquals
import org.junit.Test

class RadarAlertControllerTest {
    private var now = time("2026-08-28T12:00:00Z")
    private val effects = FakeRadarAlertEffects()
    private val controller = RadarAlertController(effects, clock = { now })
    private val sunSettings = RadarSettings(
        lightControlEnabled = true,
        lightAutoBySunEnabled = true,
    )

    @Test
    fun `daytime initialization sends off command`() {
        updateSunTimes("2026-08-28T06:00:00Z", "2026-08-28T18:00:00Z")

        assertEquals(listOf(LightMode.OFF), effects.lightModes)
    }

    @Test
    fun `enabling sun control during daytime sends off command`() {
        controller.onSunTimerTick(RadarSettings(lightControlEnabled = false))

        updateSunTimes("2026-08-28T06:00:00Z", "2026-08-28T18:00:00Z")

        assertEquals(listOf(LightMode.OFF), effects.lightModes)
    }

    @Test
    fun `nighttime initialization turns light on`() {
        now = time("2026-08-28T20:00:00Z")

        updateSunTimes("2026-08-28T06:00:00Z", "2026-08-28T18:00:00Z")

        assertEquals(listOf(LightMode.STEADY_HIGH), effects.lightModes)
    }

    @Test
    fun `failed light command is retried on next timer tick`() {
        now = time("2026-08-28T20:00:00Z")
        effects.lightCommandSucceeds = false
        updateSunTimes("2026-08-28T06:00:00Z", "2026-08-28T18:00:00Z")
        effects.lightCommandSucceeds = true

        controller.onSunTimerTick(sunSettings)

        assertEquals(
            listOf(LightMode.STEADY_HIGH, LightMode.STEADY_HIGH),
            effects.lightModes,
        )
    }

    @Test
    fun `light mode is reapplied after reconnect`() {
        now = time("2026-08-28T20:00:00Z")
        updateSunTimes("2026-08-28T06:00:00Z", "2026-08-28T18:00:00Z")

        controller.onLightUnavailable()
        controller.onLightAvailable(sunSettings)

        assertEquals(
            listOf(LightMode.STEADY_HIGH, LightMode.STEADY_HIGH),
            effects.lightModes,
        )
    }

    @Test
    fun `changed mode is applied while light remains on`() {
        now = time("2026-08-28T20:00:00Z")
        updateSunTimes("2026-08-28T06:00:00Z", "2026-08-28T18:00:00Z")

        controller.onSunTimerTick(sunSettings.copy(lightControlMode = LightMode.SLOW_FLASH))

        assertEquals(
            listOf(LightMode.STEADY_HIGH, LightMode.SLOW_FLASH),
            effects.lightModes,
        )
    }

    @Test
    fun `paused ride does not prevent radar light`() {
        controller.onRadarUpdate(
            threatLevel = 1.0,
            rideState = RideState.Paused(auto = false),
            settings = RadarSettings(lightControlEnabled = true),
        )

        assertEquals(listOf(LightMode.STEADY_HIGH), effects.lightModes)
    }

    @Test
    fun `global disabled setting does not send light command`() {
        controller.onSunTimerTick(
            sunSettings.copy(enabled = false),
        )

        assertEquals(emptyList<LightMode>(), effects.lightModes)
    }

    @Test
    fun `all clear keeps light on after sunset`() {
        now = time("2026-08-28T20:00:00Z")
        updateSunTimes("2026-08-28T06:00:00Z", "2026-08-28T18:00:00Z")
        effects.lightModes.clear()
        controller.onRadarUpdate(1.0, RideState.Idle, sunSettings)
        controller.onRadarUpdate(0.0, RideState.Idle, sunSettings)
        now += KarooRadarExtension.ALL_CLEAR_DELAY_MS + 1

        controller.onRadarUpdate(0.0, RideState.Idle, sunSettings)

        assertEquals(emptyList<LightMode>(), effects.lightModes)
    }

    private fun updateSunTimes(sunrise: String, sunset: String) {
        controller.onSunriseUpdated(time(sunrise), sunSettings)
        controller.onSunsetUpdated(time(sunset), sunSettings)
    }

    private fun time(value: String): Long = Instant.parse(value).toEpochMilli()

    private class FakeRadarAlertEffects : RadarAlertEffects {
        val lightModes = mutableListOf<LightMode>()
        var lightCommandSucceeds = true

        override fun wakeScreen() = Unit
        override fun playThreatBeep(frequency: Int, duration: Int, count: Int) = Unit
        override fun playAllClearBeep(frequency: Int, duration: Int) = Unit
        override fun logThreatDetected() = Unit
        override fun logAllClear() = Unit

        override fun setLightMode(mode: LightMode): Boolean {
            lightModes += mode
            return lightCommandSucceeds
        }
    }
}
