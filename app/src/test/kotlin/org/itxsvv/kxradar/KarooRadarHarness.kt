package org.itxsvv.kxradar

import fi.nikosavola.karooext.testing.SensorPoints
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import fi.nikosavola.karooext.testing.robolectric.RobolectricPump
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.PlayBeepPattern
import io.hammerhead.karooext.models.RideState
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.robolectric.shadows.ShadowLog

// Shared setup for the tests that run the real service against the fake Karoo system.

internal fun FakeKarooRule.startRadarExtension(
    settings: RadarSettings = RadarSettings(),
    rideState: RideState = RideState.Recording,
) {
    runBlocking { saveSettings(app, settings) }
    startRadarService(rideState)
}

// Starts the service over whatever settings are already stored, for tests that write raw values.
internal fun FakeKarooRule.startRadarService(rideState: RideState = RideState.Recording) {
    system.setRideState(rideState)
    host<KarooRadarExtension>()
    awaitStreamConsumer(DataType.Type.RADAR)
    awaitValue { system.consumerParams.firstOrNull { it is RideState.Params } }
}

internal fun FakeKarooRule.publishThreat(level: Int, vararg targetRanges: Double) {
    system.setDataPoint(SensorPoints.radar(level, *targetRanges))
}

internal fun FakeKarooRule.effectMark(): Int = system.effects.size

// Keeps publishing cleared readings, moving the clock on between tries, and returns the first beep
// from [mark] on. The service consumes points asynchronously and the all-clear delay runs on the
// looper clock, so one clear plus one clock advance can land either side of each other; retrying
// both takes the assertion off that race.
internal fun FakeKarooRule.awaitAllClear(
    mark: Int = effectMark(),
    timeoutMs: Long = 20_000,
): PlayBeepPattern =
    awaitValue(timeoutMs) {
        RobolectricPump.advanceBy(1.seconds)
        system.setDataPoint(SensorPoints.radar(0))
        system.effects.drop(mark).filterIsInstance<PlayBeepPattern>().firstOrNull()
    }

// The light command travels to Karoo's sensor service over a private AIDL interface the fakes do
// not stand in for, so its result is only visible in the service's own log.
internal fun FakeKarooRule.awaitLog(
    timeoutMs: Long = 20_000,
    predicate: (String) -> Boolean,
): String =
    awaitValue(timeoutMs) {
        logs().lastOrNull(predicate)
    }

internal fun FakeKarooRule.logs(): List<String> =
    ShadowLog.getLogsForTag(KarooRadarExtension.TAG).map { it.msg }

// The sun location the service has persisted, or null when it has saved none.
internal fun FakeKarooRule.storedSunLocation(): SunLocation? =
    runBlocking { SunLocationRepository(app).location.first() }

// Writes settings while the service is running. The edit has to come from another thread, since a
// blocking one on the test thread never gets its commit pumped through.
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal fun FakeKarooRule.changeSettings(settings: RadarSettings) {
    val write = CoroutineScope(Dispatchers.IO).async { saveSettings(app, settings) }
    awaitValue { write.takeIf { it.isCompleted }?.getCompleted() }
}

// Waits out real time while pumping, giving a change made on another thread time to reach the
// service. Only for assertions that an effect does not arrive.
internal fun FakeKarooRule.settle(timeoutMs: Long = 500) {
    runCatching { awaitValue<Int>(timeoutMs) { null } }
}
