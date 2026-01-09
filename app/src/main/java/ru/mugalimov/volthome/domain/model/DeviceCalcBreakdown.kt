package ru.mugalimov.volthome.domain.model

/**
 * Разбор участия устройства в расчётах (для DeviceSpecSheet).
 */
data class DeviceCalcBreakdown(
    val deviceId: Long,
    val calculatedPower: CalculatedValue,
    val calculatedCurrent: CalculatedValue? = null
)