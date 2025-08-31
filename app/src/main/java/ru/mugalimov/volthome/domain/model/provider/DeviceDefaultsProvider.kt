package ru.mugalimov.volthome.domain.model.provider

import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Voltage

// Один источник правды для мощностей, PF и т.д
interface DeviceDefaultsProvider { operator fun get(type: DeviceType): DeviceDefaults }

data class DeviceDefaults(
    val power: Int,                    // Номинальная мощность, Вт
    val powerFactor: Double,           // cosφ
    val demandRatio: Double,           // коэффициент спроса
    val voltage: Voltage,              // твой класс Voltage
    val hasMotor: Boolean = false,     // двигатель
    val requiresDedicatedCircuit: Boolean = false, // выделенная линия
    val requiresSocketConnection: Boolean = true   // требует розетку
)


