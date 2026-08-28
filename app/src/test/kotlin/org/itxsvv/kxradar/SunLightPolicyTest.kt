package org.itxsvv.kxradar

import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SunLightPolicyTest {
    private val policy = SunLightPolicy()
    private val sunrise = time("2026-08-28T06:00:00Z")
    private val sunset = time("2026-08-28T18:00:00Z")

    @Test
    fun `light is active before sunrise`() {
        assertTrue(isActiveAt("2026-08-28T05:59:59Z"))
    }

    @Test
    fun `light turns off at sunrise`() {
        assertFalse(isActiveAt("2026-08-28T06:00:00Z"))
    }

    @Test
    fun `light remains off during daytime`() {
        assertFalse(isActiveAt("2026-08-28T12:00:00Z"))
    }

    @Test
    fun `light turns on at sunset`() {
        assertTrue(isActiveAt("2026-08-28T18:00:00Z"))
    }

    @Test
    fun `offsets move sunrise and sunset boundaries`() {
        assertTrue(
            policy.isSunWindowActive(
                now = time("2026-08-28T06:15:00Z"),
                sunriseTime = sunrise,
                sunsetTime = sunset,
                sunriseOffsetMinutes = 30,
                sunsetOffsetMinutes = -30,
            ),
        )
        assertTrue(
            policy.isSunWindowActive(
                now = time("2026-08-28T17:30:00Z"),
                sunriseTime = sunrise,
                sunsetTime = sunset,
                sunriseOffsetMinutes = 30,
                sunsetOffsetMinutes = -30,
            ),
        )
    }

    private fun isActiveAt(value: String): Boolean {
        return policy.isSunWindowActive(
            now = time(value),
            sunriseTime = sunrise,
            sunsetTime = sunset,
            sunriseOffsetMinutes = 0,
            sunsetOffsetMinutes = 0,
        )
    }

    private fun time(value: String): Long = Instant.parse(value).toEpochMilli()
}
