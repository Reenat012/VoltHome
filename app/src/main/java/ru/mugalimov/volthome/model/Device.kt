package ru.mugalimov.volthome.model

import java.util.Date

data class Device(
    val id: Long,
    val name: String,
    val power: Int, //мощность
    val voltage: Int, //Класс напряжения
    val demandRatio: Double, //к-т спроса
    val roomId: Long,
    val createdAt: Date
)
