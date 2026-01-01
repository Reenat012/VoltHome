package ru.mugalimov.volthome.domain.model.phase_load

data class ThreePhaseLoadItem(
    val groupId: Long,
    val groupNumber: Int,
    val roomId: Long,
    val roomName: String,
    val title: String,
    val totalPower: Double,
    val totalCurrent: Double
)