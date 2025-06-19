package ru.mugalimov.volthome.domain.model

import java.util.Date

data class Device(
    val id: Long = 0,
    val name: String,
    val power: Int, //мощность
    val voltage: Voltage,
    val demandRatio: Double, //к-т спроса
    val roomId: Long,
    val createdAt: Date = Date(),
    val deviceType: DeviceType,
    val powerFactor: Double
) {
    val current: Double
        get() = calculateCurrent()

    private fun calculateCurrent(): Double {
        return when (voltage) {
            Voltage.V220 -> {
                // Для однофазных систем: I = P / (U * cosφ)
                power.toDouble() / (voltage.value * powerFactor)
            }
            else -> {
                // Для трехфазных систем: I = P / (√3 * U * cosφ)
                power.toDouble() / (1.732 * voltage.value * powerFactor)
            }
        }
    }
}
