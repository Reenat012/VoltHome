package ru.mugalimov.volthome.domain.model

// Класс для подгруппы с параметрами
data class SubGroup(
    val devices: List<Device>,
    val totalCurrent: Double,
    val circuitBreaker: Int,
    val cableCrossSection: Double
)