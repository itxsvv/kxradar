package org.itxsvv.kxradar

import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SunTimesCalculatorTest {
    private val calculator = LibrarySunTimesCalculator(TimeZone.getTimeZone("Europe/Belgrade"))

    @Test
    fun `Novi Sad is in night window at 1940 on August 28`() {
        val now = time("2026-08-28T17:40:00Z")
        val result = calculator.calculate(
            now,
            SunLocation(latitude = 45.2671, longitude = 19.8335),
        )
        assertNotNull(result)
        val sunTimes = result!!

        assertTrue(sunTimes.sunriseTime < now)
        assertTrue(sunTimes.sunsetTime < now)
        assertTrue(
            SunLightPolicy().isSunWindowActive(
                now = now,
                sunriseTime = sunTimes.sunriseTime,
                sunsetTime = sunTimes.sunsetTime,
                sunriseOffsetMinutes = 0,
                sunsetOffsetMinutes = 0,
            ),
        )
    }

    @Test
    fun `invalid location is rejected`() {
        assertNull(
            calculator.calculate(
                time("2026-08-28T17:40:00Z"),
                SunLocation(latitude = 100.0, longitude = 19.8335),
            ),
        )
    }

    private fun time(value: String): Long = Instant.parse(value).toEpochMilli()
}
