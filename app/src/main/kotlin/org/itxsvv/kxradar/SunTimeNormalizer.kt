package org.itxsvv.kxradar

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

internal class SunTimeNormalizer(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    fun toEpochMillis(value: Double, now: Long): Long? {
        if (!value.isFinite() || value <= 0.0) {
            return null
        }
        val rawValue = value.toLong()
        val normalizedValue = when {
            rawValue <= SECONDS_PER_DAY -> startOfDay(now) + rawValue * MILLIS_PER_SECOND
            rawValue <= MILLIS_PER_DAY -> startOfDay(now) + rawValue
            rawValue < EPOCH_MILLIS_THRESHOLD -> rawValue * MILLIS_PER_SECOND
            else -> rawValue
        }
        return normalizedValue.takeIf { abs(it - now) <= MAX_TIME_DISTANCE_MS }
    }

    private fun startOfDay(now: Long): Long {
        return Instant.ofEpochMilli(now)
            .atZone(zoneId)
            .toLocalDate()
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    private companion object {
        const val SECONDS_PER_DAY = 86_400L
        const val MILLIS_PER_SECOND = 1_000L
        const val MILLIS_PER_DAY = SECONDS_PER_DAY * MILLIS_PER_SECOND
        const val EPOCH_MILLIS_THRESHOLD = 100_000_000_000L
        const val MAX_TIME_DISTANCE_MS = 2 * MILLIS_PER_DAY
    }
}
