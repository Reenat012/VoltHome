package ru.mugalimov.volthome.domain.model.singleline

import ru.mugalimov.volthome.domain.model.DeviceType

/**
 * Краткое описание устройства внутри группы.
 *
 * Используется для компактного списка нагрузок в PDF-схеме.
 */
data class SingleLineDeviceSummary(
    val deviceId: Long,
    val name: String,
    val roomName: String?,
    val type: DeviceType?,
    val powerWatts: Double?,
    val calculatedPowerWatts: Double?,
    val calculatedCurrentAmps: Double?
)