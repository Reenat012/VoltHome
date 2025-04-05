package ru.mugalimov.volthome.domain.model

// Готовые устройства-шаблоны (без привязки к комнате, room_id = 0)
data class DefaultDevice(
    val id: Long,
    val name: String,
    val power: Int,
    val voltage: Int,
    val demandRatio: Double
)
