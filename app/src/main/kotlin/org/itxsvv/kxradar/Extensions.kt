package org.itxsvv.kxradar

import android.content.Context
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.PlayBeepPattern
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json

val jsonWithUnknownKeys = Json { ignoreUnknownKeys = true }

suspend fun saveSettings(context: Context, settings: RadarSettings) {
    RadarSettingsService(context).save(settings)
}

fun Context.streamSettings(): Flow<RadarSettings> {
    return RadarSettingsService(this).settings
}

fun KarooSystemService.streamDataFlow(dataTypeId: String): Flow<StreamState> {
    return callbackFlow {
        val listenerId =
            addConsumer(OnStreamState.StartStreaming(dataTypeId)) { event: OnStreamState ->
                trySendBlocking(event.state)
            }
        awaitClose {
            removeConsumer(listenerId)
        }
    }
}

fun KarooSystemService.streamRideState(): Flow<RideState> {
    return callbackFlow {
        val listenerId = addConsumer { rideState: RideState ->
            trySendBlocking(rideState)
        }
        awaitClose {
            removeConsumer(listenerId)
        }
    }
}

fun KarooSystemService.beep(freq: Int, duration: Int) {
    beep(freq, duration, 1)
}

fun KarooSystemService.beep(freq: Int, duration: Int, count: Int) {
    val beepList = mutableListOf(PlayBeepPattern.Tone(freq, duration))
    repeat(count - 1) {
        beepList.add(PlayBeepPattern.Tone(0, 50))
        beepList.add(PlayBeepPattern.Tone(freq, duration))
    }
    dispatch(PlayBeepPattern(beepList))
}
