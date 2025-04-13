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
    val deviceType: DeviceType
) {
    val current by lazy {
        if (voltage == Voltage.V220) {
            (power / voltage.value).toDouble()
        } else (power / (1.73 * voltage.value * 0.95))
        }
}
