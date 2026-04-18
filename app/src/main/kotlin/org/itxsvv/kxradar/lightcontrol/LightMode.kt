package org.itxsvv.kxradar.lightcontrol

import kotlinx.serialization.Serializable

/**
 * (C) https://github.com/derstrassi/karoofirefly
 */
@Serializable
enum class LightMode(val modeNumber: Int, val displayName: String, val karooName: String) {
    OFF(0, "Off", "OFF"),
    STEADY_HIGH(1, "Steady High", "STEADY4"),
    STEADY_LOW(2, "Steady Low", "STEADY5"),
    SLOW_FLASH(3, "Slow Flash", "SLOW_FLASH"),
    FAST_FLASH(4, "Fast Flash", "FAST_FLASH"),
    CUSTOM_1(5, "Custom 1", "CUSTOM_MODE_1"),
    CUSTOM_2(6, "Custom 2", "CUSTOM_MODE_2"),
    ;

    companion object {
        fun fromModeNumber(number: Int): org.itxsvv.kxradar.lightcontrol.LightMode? {
            return org.itxsvv.kxradar.lightcontrol.LightMode.entries.find { it.modeNumber == number }
        }

        val CYCLING_MODES = listOf(OFF, STEADY_HIGH, STEADY_LOW, SLOW_FLASH, FAST_FLASH)
    }
}
