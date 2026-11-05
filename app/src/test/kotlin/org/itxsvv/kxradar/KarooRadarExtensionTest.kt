package org.itxsvv.kxradar

import fi.nikosavola.karooext.testing.SensorPoints
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.PlayBeepPattern
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.TurnScreenOn
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The real service watches the radar stream on a fake Karoo system. The SDK it is built against is
// 1.1.5, older than the one the test library is compiled with.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KarooRadarExtensionTest {
    @get:Rule val karoo = FakeKarooRule()

    private fun start(settings: RadarSettings = RadarSettings(), rideState: RideState = RideState.Recording) {
        runBlocking { saveSettings(karoo.app, settings) }
        karoo.system.setRideState(rideState)
        karoo.host<KarooRadarExtension>()
        karoo.awaitStreamConsumer(DataType.Type.RADAR)
        karoo.awaitValue { karoo.system.consumerParams.firstOrNull { it is RideState.Params } }
    }

    private fun threat(level: Int) = karoo.system.setDataPoint(SensorPoints.radar(level, 20.0))

    @Test
    fun `a threat wakes the screen and beeps`() {
        start()

        threat(1)

        karoo.awaitEffect<TurnScreenOn>()
        val beep = karoo.awaitEffect<PlayBeepPattern>()
        assertEquals(listOf(PlayBeepPattern.Tone(200, 100)), beep.tones)
    }

    @Test
    fun `a red threat beeps twice when asked to`() {
        start(RadarSettings(redThreadAlert = true))

        threat(2)

        val beep = karoo.awaitEffect<PlayBeepPattern>()
        assertEquals(
            listOf(PlayBeepPattern.Tone(200, 100), PlayBeepPattern.Tone(0, 50), PlayBeepPattern.Tone(200, 100)),
            beep.tones,
        )
    }

    @Test
    fun `in-ride-only keeps the beep quiet until the ride is recording`() {
        start(RadarSettings(inRideOnly = true), rideState = RideState.Idle)

        threat(1)

        karoo.awaitEffect<TurnScreenOn>()
        karoo.assertNoEffect<PlayBeepPattern>()
    }

    @Test
    fun `a disabled radar alert does nothing`() {
        start(RadarSettings(enabled = false))

        threat(1)

        karoo.assertNoEffect<TurnScreenOn>()
        karoo.assertNoEffect<PlayBeepPattern>()
    }
}
