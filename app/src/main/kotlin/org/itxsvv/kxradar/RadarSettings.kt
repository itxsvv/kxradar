package org.itxsvv.kxradar

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class Beep(
    val frequency: Int,
    val duration: Int,
)

@Serializable
data class RadarSettings(
    val threatBeep: Beep = Beep(200, 100),
    val passedBeep: Beep = Beep(0, 100),
    val inRideOnly: Boolean = false,
    val enabled: Boolean = true,
    val wakeUpScreen: Boolean = true,
    val redThreadAlert: Boolean = false,
) {
    companion object {
        val defaultSettings = jsonWithUnknownKeys.encodeToString(RadarSettings())
    }
}
