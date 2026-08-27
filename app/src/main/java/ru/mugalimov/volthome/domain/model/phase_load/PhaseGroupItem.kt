package ru.mugalimov.volthome.domain.model.phase_load

data class PhaseGroupItem(
    val groupId: Long,
    val groupNumber: Int,
    val roomName: String,
    val roomId: Long,
    val devices: List<PhaseDeviceItem>, // Названия устройств
    val totalPower: Double, // Вт
    /** Расчётный ток группы. */
    val totalCurrent: Double,
    /** Паспортный ток группы. */
    val installedCurrent: Double = totalCurrent
)
