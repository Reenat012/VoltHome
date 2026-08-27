package ru.mugalimov.volthome.domain.model.phase_load

data class PhaseDeviceItem(
    val deviceId: Long,
    val name: String,
    val power: Double,
    /** Расчётный ток с коэффициентом спроса. */
    val current: Double,
    /** Паспортный ток без коэффициента спроса. */
    val installedCurrent: Double = current
)
