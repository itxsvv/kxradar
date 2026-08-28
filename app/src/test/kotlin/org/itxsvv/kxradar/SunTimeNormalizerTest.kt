package org.itxsvv.kxradar

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SunTimeNormalizerTest {
    private val normalizer = SunTimeNormalizer(ZoneId.of("UTC"))
    private val now = time("2026-08-28T12:00:00Z")

    @Test
    fun `epoch milliseconds remain unchanged`() {
        val sunrise = time("2026-08-28T06:00:00Z")

        assertEquals(sunrise, normalizer.toEpochMillis(sunrise.toDouble(), now))
    }

    @Test
    fun `epoch seconds are converted to milliseconds`() {
        val sunrise = time("2026-08-28T06:00:00Z")

        assertEquals(sunrise, normalizer.toEpochMillis(sunrise / 1_000.0, now))
    }

    @Test
    fun `seconds from start of day are converted to current date`() {
        assertEquals(
            time("2026-08-28T06:00:00Z"),
            normalizer.toEpochMillis(6 * 60 * 60.0, now),
        )
    }

    @Test
    fun `stale timestamp is rejected`() {
        assertNull(normalizer.toEpochMillis(time("2020-01-01T06:00:00Z").toDouble(), now))
    }

    private fun time(value: String): Long = Instant.parse(value).toEpochMilli()
}
