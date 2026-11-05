package org.itxsvv.kxradar

import fi.nikosavola.karooext.testing.SensorPoints
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import fi.nikosavola.karooext.testing.robolectric.RobolectricPump
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.PlayBeepPattern
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.TurnScreenOn
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The service reads System.currentTimeMillis for its all-clear delay, so its package is
// instrumented to put that clock under RobolectricPump.advanceBy.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], instrumentedPackages = ["org.itxsvv.kxradar"])
class KarooRadarAlertTest {
    @get:Rule val karoo = FakeKarooRule()

    @Test
    fun `a rising threat level does not beep again while the threat persists`() {
        karoo.startRadarExtension()
        karoo.publishThreat(1)
        karoo.awaitEffect<PlayBeepPattern>()
        val mark = karoo.effectMark()

        karoo.publishThreat(3, 10.0, 12.0)

        karoo.assertNoEffect<PlayBeepPattern>(forMs = 400, after = mark)
    }

    @Test
    fun `an all-clear beeps once the threat is gone`() {
        karoo.startRadarExtension()
        karoo.publishThreat(1)
        karoo.awaitEffect<PlayBeepPattern>()
        val mark = karoo.effectMark()

        val beep = karoo.awaitAllClear(mark)

        assertEquals(listOf(PlayBeepPattern.Tone(0, 100)), beep.tones)
    }

    @Test
    fun `a cleared threat re-arms the threat beep`() {
        karoo.startRadarExtension()
        karoo.publishThreat(1)
        karoo.awaitEffect<PlayBeepPattern>()
        val mark = karoo.effectMark()
        karoo.awaitAllClear(mark)
        val cleared = karoo.effectMark()

        karoo.publishThreat(1)

        val beep = karoo.awaitEffect<PlayBeepPattern>(after = cleared)
        assertEquals(listOf(PlayBeepPattern.Tone(200, 100)), beep.tones)
    }

    @Test
    fun `a threat that returns re-alerts instead of going all-clear`() {
        karoo.startRadarExtension()
        karoo.publishThreat(1)
        karoo.awaitEffect<PlayBeepPattern>()
        val mark = karoo.effectMark()

        karoo.publishThreat(0)
        RobolectricPump.advanceBy(1.seconds)
        karoo.publishThreat(1)

        val beep = karoo.awaitEffect<PlayBeepPattern>(after = mark)
        assertEquals(listOf(PlayBeepPattern.Tone(200, 100)), beep.tones)
    }

    @Test
    fun `turning off the screen wake keeps it asleep`() {
        karoo.startRadarExtension(RadarSettings(wakeUpScreen = false))

        karoo.publishThreat(1)

        karoo.awaitEffect<PlayBeepPattern>()
        karoo.assertNoEffect<TurnScreenOn>()
    }

    @Test
    fun `red thread alert needs a level above one to double the beep`() {
        karoo.startRadarExtension(RadarSettings(redThreadAlert = true))

        karoo.publishThreat(1)

        val beep = karoo.awaitEffect<PlayBeepPattern>()
        assertEquals(listOf(PlayBeepPattern.Tone(200, 100)), beep.tones)
    }

    @Test
    fun `a paused ride wakes the screen but stays quiet in-ride-only`() {
        karoo.startRadarExtension(
            RadarSettings(inRideOnly = true),
            rideState = RideState.Paused(auto = false),
        )

        karoo.publishThreat(1)

        karoo.awaitEffect<TurnScreenOn>()
        karoo.assertNoEffect<PlayBeepPattern>()
    }

    @Test
    fun `starting to record mid-ride re-enables the threat beep`() {
        karoo.startRadarExtension(RadarSettings(inRideOnly = true), rideState = RideState.Idle)
        karoo.publishThreat(1)
        karoo.awaitEffect<TurnScreenOn>()
        karoo.assertNoEffect<PlayBeepPattern>()
        karoo.system.setRideState(RideState.Recording)

        // The clear before it is what lets the second reading read as a fresh threat, so the
        // beep also proves the service saw the ride start. The service takes the ride state
        // asynchronously, so the pair is retried until it has.
        val mark = karoo.effectMark()
        val beep = karoo.awaitValue(20_000) {
            karoo.publishThreat(0)
            karoo.publishThreat(1)
            karoo.system.effects.drop(mark).filterIsInstance<PlayBeepPattern>().firstOrNull()
        }
        assertEquals(listOf(PlayBeepPattern.Tone(200, 100)), beep.tones)
    }

    @Test
    fun `custom tones from settings are used for both beeps`() {
        karoo.startRadarExtension(
            RadarSettings(threatBeep = Beep(880, 250), passedBeep = Beep(440, 60)),
        )

        karoo.publishThreat(1)
        val threat = karoo.awaitEffect<PlayBeepPattern>()
        val mark = karoo.effectMark()

        val allClear = karoo.awaitAllClear(mark)

        assertEquals(listOf(PlayBeepPattern.Tone(880, 250)), threat.tones)
        assertEquals(listOf(PlayBeepPattern.Tone(440, 60)), allClear.tones)
    }

    @Test
    fun `target ranges without a threat level do not alert`() {
        karoo.startRadarExtension()

        karoo.publishThreat(0, 10.0, 20.0)

        karoo.assertNoEffect<TurnScreenOn>()

        karoo.publishThreat(1)
        karoo.awaitEffect<PlayBeepPattern>()
    }

    @Test
    fun `a radar that is not streaming does not alert`() {
        karoo.startRadarExtension()

        karoo.system.setStreamState(DataType.Type.RADAR, StreamState.Searching)

        karoo.assertNoEffect<TurnScreenOn>()

        karoo.publishThreat(1)
        karoo.awaitEffect<PlayBeepPattern>()
    }

    @Test
    fun `a radar reading without a threat field does not alert`() {
        karoo.startRadarExtension()

        karoo.system.setDataPoint(
            DataPoint(
                DataType.Type.RADAR,
                mapOf(DataType.Field.RADAR_TARGET_1_RANGE to 20.0),
            ),
        )

        karoo.assertNoEffect<TurnScreenOn>()

        karoo.publishThreat(1)
        karoo.awaitEffect<PlayBeepPattern>()
    }

    @Test
    fun `an all-clear stays quiet in-ride-only while the ride is not recording`() {
        karoo.startRadarExtension(RadarSettings(inRideOnly = true), rideState = RideState.Idle)
        karoo.publishThreat(1)
        karoo.awaitEffect<TurnScreenOn>()
        val mark = karoo.effectMark()

        repeat(3) {
            RobolectricPump.advanceBy(2.seconds)
            karoo.publishThreat(0)
        }

        karoo.assertNoEffect<PlayBeepPattern>(forMs = 300, after = mark)
    }

    @Test
    fun `a radar error reading with no threat does not alert`() {
        karoo.startRadarExtension()

        karoo.system.setDataPoint(SensorPoints.radar(0, error = 3))

        karoo.assertNoEffect<TurnScreenOn>()

        karoo.publishThreat(1)
        karoo.awaitEffect<PlayBeepPattern>()
    }
}
