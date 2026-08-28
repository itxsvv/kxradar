package org.itxsvv.kxradar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal data class SunAutomationUpdate(
    val sunTimes: SunTimes? = null,
    val locationToPersist: SunLocation? = null,
    val calculationAttempted: Boolean = false,
)

internal class SunAutomationCoordinator(
    private val calculator: SunTimesCalculator = LibrarySunTimesCalculator(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private var latestLocation: SunLocation? = null
    private var calculatedLocation: SunLocation? = null
    private var calculatedDate: LocalDate? = null

    @Synchronized
    fun restoreLocation(location: SunLocation): SunAutomationUpdate {
        if (latestLocation != null || !location.isValid()) {
            return SunAutomationUpdate()
        }
        latestLocation = location
        return calculate(location)
    }

    @Synchronized
    fun onLocationUpdate(location: SunLocation): SunAutomationUpdate {
        if (!location.isValid()) {
            return SunAutomationUpdate()
        }
        latestLocation = location
        val shouldRecalculate = calculatedLocation == null ||
            calculatedDate != currentDate() ||
            distanceMeters(calculatedLocation!!, location) >= RECALCULATION_DISTANCE_METERS
        if (!shouldRecalculate) {
            return SunAutomationUpdate()
        }
        return calculate(location, locationToPersist = location)
    }

    @Synchronized
    fun refresh(): SunAutomationUpdate {
        val location = latestLocation ?: return SunAutomationUpdate()
        if (calculatedDate == currentDate()) {
            return SunAutomationUpdate()
        }
        return calculate(location)
    }

    private fun calculate(
        location: SunLocation,
        locationToPersist: SunLocation? = null,
    ): SunAutomationUpdate {
        val sunTimes = calculator.calculate(clock(), location)
        if (sunTimes != null) {
            calculatedLocation = location
            calculatedDate = currentDate()
        }
        return SunAutomationUpdate(
            sunTimes = sunTimes,
            locationToPersist = locationToPersist,
            calculationAttempted = true,
        )
    }

    private fun currentDate(): LocalDate {
        return Instant.ofEpochMilli(clock()).atZone(zoneId).toLocalDate()
    }

    private fun distanceMeters(first: SunLocation, second: SunLocation): Double {
        val firstLatitude = Math.toRadians(first.latitude)
        val secondLatitude = Math.toRadians(second.latitude)
        val latitudeDelta = secondLatitude - firstLatitude
        val longitudeDelta = Math.toRadians(second.longitude - first.longitude)
        val a = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
            cos(firstLatitude) * cos(secondLatitude) *
            sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
        return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0
        const val RECALCULATION_DISTANCE_METERS = 5_000.0
    }
}
