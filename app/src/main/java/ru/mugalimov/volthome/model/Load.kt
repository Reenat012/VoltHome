package ru.mugalimov.volthome.model

import java.util.Date

data class Load(
    val id: Int,
    val name: String,
    val current: Double,
    val sumPower: Int,
    val countDevices: Int,
    val createdAt: Date,
    val roomId: Int,
)
