package org.itxsvv.kxradar

import ca.rmen.sunrisesunset.SunriseSunset
import java.util.Calendar
import java.util.TimeZone

internal data class SunLocation(
    val latitude: Double,
    val longitude: Double,
) {
    fun isValid(): Boolean {
        return latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
    }
}

internal data class SunTimes(
    val sunriseTime: Long,
    val sunsetTime: Long,
)

internal fun interface SunTimesCalculator {
    fun calculate(now: Long, location: SunLocation): SunTimes?
}

internal class LibrarySunTimesCalculator(
    private val timeZone: TimeZone = TimeZone.getDefault(),
) : SunTimesCalculator {
    override fun calculate(now: Long, location: SunLocation): SunTimes? {
        if (!location.isValid()) {
            return null
        }
        return runCatching {
            val date = Calendar.getInstance(timeZone).apply { timeInMillis = now }
            val result = SunriseSunset.getSunriseSunset(
                date,
                location.latitude,
                location.longitude,
            )
            val sunrise = result.getOrNull(0) ?: return null
            val sunset = result.getOrNull(1) ?: return null
            SunTimes(
                sunriseTime = sunrise.timeInMillis,
                sunsetTime = sunset.timeInMillis,
            )
        }.getOrNull()
    }
}
