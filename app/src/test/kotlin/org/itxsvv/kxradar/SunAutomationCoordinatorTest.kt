package org.itxsvv.kxradar

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SunAutomationCoordinatorTest {
    private var now = time("2026-08-28T12:00:00Z")
    private val calculatedLocations = mutableListOf<SunLocation>()
    private val calculator = SunTimesCalculator { calculationTime, location ->
        calculatedLocations += location
        SunTimes(
            sunriseTime = calculationTime - 6 * 60 * 60 * 1_000L,
            sunsetTime = calculationTime + 6 * 60 * 60 * 1_000L,
        )
    }
    private val coordinator = SunAutomationCoordinator(
        calculator = calculator,
        clock = { now },
        zoneId = ZoneId.of("UTC"),
    )

    @Test
    fun `first GPS location is calculated and persisted`() {
        val location = SunLocation(45.2671, 19.8335)

        val update = coordinator.onLocationUpdate(location)

        assertNotNull(update.sunTimes)
        assertEquals(location, update.locationToPersist)
        assertEquals(listOf(location), calculatedLocations)
    }

    @Test
    fun `nearby GPS update does not trigger recalculation`() {
        coordinator.onLocationUpdate(SunLocation(45.2671, 19.8335))

        val update = coordinator.onLocationUpdate(SunLocation(45.2681, 19.8335))

        assertNull(update.sunTimes)
        assertNull(update.locationToPersist)
        assertEquals(1, calculatedLocations.size)
    }

    @Test
    fun `significant movement triggers recalculation and persistence`() {
        coordinator.onLocationUpdate(SunLocation(45.2671, 19.8335))
        val movedLocation = SunLocation(45.3671, 19.8335)

        val update = coordinator.onLocationUpdate(movedLocation)

        assertNotNull(update.sunTimes)
        assertEquals(movedLocation, update.locationToPersist)
        assertEquals(2, calculatedLocations.size)
    }

    @Test
    fun `new date recalculates sun times from latest location`() {
        val location = SunLocation(45.2671, 19.8335)
        coordinator.restoreLocation(location)
        now += 24 * 60 * 60 * 1_000L

        val update = coordinator.refresh()

        assertNotNull(update.sunTimes)
        assertNull(update.locationToPersist)
        assertEquals(listOf(location, location), calculatedLocations)
    }

    @Test
    fun `late cache restore does not overwrite live GPS location`() {
        val liveLocation = SunLocation(45.2671, 19.8335)
        coordinator.onLocationUpdate(liveLocation)

        val update = coordinator.restoreLocation(SunLocation(40.0, 10.0))

        assertNull(update.sunTimes)
        assertEquals(listOf(liveLocation), calculatedLocations)
    }

    private fun time(value: String): Long = Instant.parse(value).toEpochMilli()
}
