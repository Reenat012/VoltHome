package ru.mugalimov.volthome.domain.model.phase_load

data class PhaseGroupItem(
    val groupNumber: Int,
    val roomName: String,
    val devices: List<String>, // Названия устройств
    val totalPower: Double, // Вт
    val totalCurrent: Double // А
)

