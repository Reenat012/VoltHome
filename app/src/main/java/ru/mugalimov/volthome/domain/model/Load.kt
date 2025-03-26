package ru.mugalimov.volthome.domain.model

import java.util.Date

data class Load(
    val id: Long,
    val name: String,
    val current: Double,
    val sumPower: Int,
    val countDevices: Int,
    val createdAt: Date,
    val roomId: Long,
)
