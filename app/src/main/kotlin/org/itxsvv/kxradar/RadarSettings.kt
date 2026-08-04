package org.itxsvv.kxradar

import org.itxsvv.kxradar.lightcontrol.LightMode
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
    val lightControlEnabled: Boolean = false,
    val lightControlMode: LightMode = LightMode.STEADY_HIGH,
    val lightAutoBySunEnabled: Boolean = false,
    val lightSunsetOffsetMinutes: Int = 0,
    val lightSunriseOffsetMinutes: Int = 0,
) {
    companion object {
        val defaultSettings = jsonWithUnknownKeys.encodeToString(RadarSettings())
    }
}
