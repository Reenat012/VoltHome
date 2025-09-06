package ru.mugalimov.volthome.domain.model.phase_load

data class PhaseGroupItem(
    val groupNumber: Int,
    val roomName: String,
    val roomId: Long,
    val devices: List<PhaseDeviceItem>, // Названия устройств
    val totalPower: Double, // Вт
    val totalCurrent: Double // А
)

