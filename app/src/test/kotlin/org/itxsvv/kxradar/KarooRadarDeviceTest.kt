package org.itxsvv.kxradar

import fi.nikosavola.karooext.testing.FakeKarooSystem
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnLocationChanged
import io.hammerhead.karooext.models.PlayBeepPattern
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.SavedDevices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

// Same package-level clock control as the alert tests: the light state machine behind the radar
// alert reads the wall clock, and the saved-device discovery does not depend on it.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], instrumentedPackages = ["org.itxsvv.kxradar"])
class KarooRadarDeviceTest {
    @get:Rule val karoo = FakeKarooRule()

    @Before
    fun clearLogs() {
        ShadowLog.clear()
    }

    @Test
    fun `startup subscribes to the radar, the ride state, location events and saved devices`() {
        karoo.startRadarExtension()

        karoo.awaitStreamConsumer(DataType.Type.RADAR)
        karoo.awaitConsumer(RideState.Params)
        karoo.awaitConsumer(OnLocationChanged.Params)
        karoo.awaitConsumer(SavedDevices.Params)
    }

    @Test
    fun `a restart mid-ride re-subscribes and alerts again`() {
        karoo.startRadarExtension()
        karoo.publishThreat(1)
        karoo.awaitEffect<PlayBeepPattern>()

        karoo.restart<KarooRadarExtension>()
        karoo.awaitStreamConsumer(DataType.Type.RADAR)
        val mark = karoo.effectMark()

        karoo.publishThreat(0)
        karoo.publishThreat(1)

        val beep = karoo.awaitEffect<PlayBeepPattern>(after = mark)
        assertEquals(listOf(PlayBeepPattern.Tone(200, 100)), beep.tones)
    }

    @Test
    fun `a saved bike light is reported and used as the rear light`() {
        karoo.system.setSavedDevices(
            listOf(
                FakeKarooSystem.savedDevice(
                    id = "28691-35-5",
                    name = "Seat post light",
                    "TYPE_BIKE_LIGHT_ID",
                ),
            ),
        )
        karoo.startRadarExtension(RadarSettings(lightControlEnabled = true))

        karoo.publishThreat(1)

        karoo.awaitLog { it.contains("lightId=28691-35-5") }
        assertTrue(karoo.logs().any { it.contains("Found 1 saved bike light(s)") })
    }

    @Test
    fun `a saved light of another device type is not the rear light`() {
        karoo.system.setSavedDevices(
            listOf(
                FakeKarooSystem.savedDevice(
                    id = "28691-34-5",
                    name = "Front light",
                    "TYPE_BIKE_LIGHT_ID",
                ),
            ),
        )
        karoo.startRadarExtension(RadarSettings(lightControlEnabled = true))

        karoo.publishThreat(1)

        karoo.awaitLog { it.contains("rear light is unavailable") }
        assertTrue(karoo.logs().none { it.contains("lightId=") })
    }

    @Test
    fun `a disabled saved light is not the rear light`() {
        karoo.system.setSavedDevices(
            listOf(
                FakeKarooSystem.savedDevice(
                    id = "28691-35-5",
                    name = "Seat post light",
                    "TYPE_BIKE_LIGHT_ID",
                    enabled = false,
                ),
            ),
        )
        karoo.startRadarExtension(RadarSettings(lightControlEnabled = true))

        karoo.publishThreat(1)

        karoo.awaitLog { it.contains("rear light is unavailable") }
    }

    @Test
    fun `a saved device without the light data type is not the rear light`() {
        karoo.system.setSavedDevices(
            listOf(
                FakeKarooSystem.savedDevice(
                    id = "28691-35-5",
                    name = "Power meter",
                    "TYPE_POWER_ID",
                ),
            ),
        )
        karoo.startRadarExtension(RadarSettings(lightControlEnabled = true))

        karoo.publishThreat(1)

        karoo.awaitLog { it.contains("rear light is unavailable") }
    }

    @Test
    fun `a saved light id without a type segment is not the rear light`() {
        karoo.system.setSavedDevices(
            listOf(
                FakeKarooSystem.savedDevice(
                    id = "28691-35",
                    name = "Seat post light",
                    "TYPE_BIKE_LIGHT_ID",
                ),
            ),
        )
        karoo.startRadarExtension(RadarSettings(lightControlEnabled = true))

        karoo.publishThreat(1)

        karoo.awaitLog { it.contains("rear light is unavailable") }
    }

    @Test
    fun `a light saved while the ride is running is picked up`() {
        karoo.startRadarExtension(RadarSettings(lightControlEnabled = true))
        karoo.publishThreat(1)
        karoo.awaitLog { it.contains("rear light is unavailable") }

        karoo.system.setSavedDevices(listOf(seatPostLight()))

        karoo.awaitLog { it.contains("lightId=28691-35-5") }
    }

    @Test
    fun `a light removed while the ride is running is dropped`() {
        karoo.system.setSavedDevices(listOf(seatPostLight()))
        karoo.startRadarExtension(RadarSettings(lightControlEnabled = true))
        karoo.publishThreat(1)
        karoo.awaitLog { it.contains("lightId=28691-35-5") }

        karoo.system.setSavedDevices(emptyList())

        karoo.awaitLog { it.contains("Rear light unavailable") }
    }

    private fun seatPostLight() =
        FakeKarooSystem.savedDevice(
            id = "28691-35-5",
            name = "Seat post light",
            "TYPE_BIKE_LIGHT_ID",
        )
}
