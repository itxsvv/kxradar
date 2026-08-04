package org.itxsvv.kxradar

class SunLightPolicy {
    fun isSunWindowActive(
        now: Long,
        sunriseTime: Long,
        sunsetTime: Long,
        sunriseOffsetMinutes: Int,
        sunsetOffsetMinutes: Int,
    ): Boolean {
        val sunriseBoundary = sunriseTime + sunriseOffsetMinutes * 60_000L
        val sunsetBoundary = sunsetTime + sunsetOffsetMinutes * 60_000L
        return now >= sunsetBoundary || now < sunriseBoundary
    }
}
