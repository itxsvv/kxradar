package org.itxsvv.kxradar

import io.hammerhead.karooext.models.DataType

/**
 * Main idea:
 * If secondary threats are approaching and the delay between it and the previous threat
 * is more than 2 seconds - make a new beep.
 */
class ThreatsMonitor {
    private var targets = mapOf(
        DataType.Field.RADAR_TARGET_1_RANGE to 0L,
        DataType.Field.RADAR_TARGET_2_RANGE to 0L,
        DataType.Field.RADAR_TARGET_3_RANGE to 0L,
        DataType.Field.RADAR_TARGET_4_RANGE to 0L,
        DataType.Field.RADAR_TARGET_5_RANGE to 0L,
        DataType.Field.RADAR_TARGET_6_RANGE to 0L,
        DataType.Field.RADAR_TARGET_7_RANGE to 0L,
        DataType.Field.RADAR_TARGET_8_RANGE to 0L
    )
}