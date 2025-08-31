package ru.mugalimov.volthome.domain.model

// ui model, чтобы не зависеть от доменной DefaultDevice
data class DeviceSpecUi(
    val name: String,
    val power: Int,
    val voltage: Int?,                 // Вольты, если есть
    val demandRatio: Double?,          // если нет — покажем «—»
    val powerFactor: Double?,
    val deviceType: String?,
    val hasMotor: Boolean,
    val requiresDedicatedCircuit: Boolean,
    val requiresSocketConnection: Boolean? // если есть в доменной модели
)
