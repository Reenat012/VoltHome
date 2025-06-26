package ru.mugalimov.volthome.domain.model

import java.util.Date

data class TotalLoad(
    val id: Long = -1,
    val name: String = "Общая нагрузка",
    val totalPower: Int,
    val totalDevices: Int,
    val totalCurrent: Double
)
