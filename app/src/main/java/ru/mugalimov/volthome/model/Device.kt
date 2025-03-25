package ru.mugalimov.volthome.model

import java.util.Date

data class Device(
    val id: Int,
    val name: String,
    val power: Int, //мощность
    val voltage: Int, //Класс напряжения
    val demandRatio: Double, //к-т спроса
    val roomId: Int,
    val createdAt: Date
)
