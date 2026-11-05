package org.itxsvv.kxradar

import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import io.hammerhead.karooext.models.OnLocationChanged
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The service saves the sun location it derives from Karoo fixes, which is the only part of the sun
// automation observable without a light to command.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], instrumentedPackages = ["org.itxsvv.kxradar"])
class KarooRadarSunLocationTest {
    @get:Rule val karoo = FakeKarooRule()

    @Test
    fun `a Karoo fix is saved for the next ride`() {
        karoo.startRadarExtension()
        karoo.awaitConsumer(OnLocationChanged.Params)

        karoo.system.setLocation(45.2671, 19.8335)

        val stored = karoo.awaitValue {
            karoo.storedSunLocation()?.takeIf { it.latitude == 45.2671 }
        }
        assertEquals(SunLocation(45.2671, 19.8335), stored)
    }

    @Test
    fun `an out-of-range Karoo fix is not saved`() {
        karoo.startRadarExtension()
        karoo.awaitConsumer(OnLocationChanged.Params)
        val before = karoo.storedSunLocation()

        karoo.system.setLocation(200.0, 19.8335)
        karoo.settle(timeoutMs = 800)

        assertEquals(before, karoo.storedSunLocation())
    }

    @Test
    fun `a moved fix is saved only once the rider has gone far enough`() {
        karoo.startRadarExtension()
        karoo.awaitConsumer(OnLocationChanged.Params)
        karoo.system.setLocation(45.2671, 19.8335)
        karoo.awaitValue { karoo.storedSunLocation()?.takeIf { it.latitude == 45.2671 } }

        karoo.system.setLocation(45.2681, 19.8335)
        karoo.settle(timeoutMs = 800)

        assertEquals(SunLocation(45.2671, 19.8335), karoo.storedSunLocation())

        karoo.system.setLocation(45.3671, 19.8335)

        val moved = karoo.awaitValue {
            karoo.storedSunLocation()?.takeIf { it.latitude == 45.3671 }
        }
        assertEquals(SunLocation(45.3671, 19.8335), moved)
    }
}
