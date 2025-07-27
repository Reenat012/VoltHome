package ru.mugalimov.volthome.domain.model

import androidx.room.ColumnInfo
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
    val powerFactor: Double,
    val hasMotor: Boolean = false,  // Имеет ли двигатель
    val requiresDedicatedCircuit: Boolean = false,  // Требует выделенной линии
    val requiresSocketConnection: Boolean = true // Для явного указания типа подключения
) {
    val current: Double
        get() = calculateCurrent()

    fun calculateCurrent(): Double {
        return when (voltage.type) {
            VoltageType.AC_1PHASE ->
                power.toDouble() / (voltage.value * powerFactor)
            VoltageType.AC_3PHASE ->
                power.toDouble() / (1.732 * voltage.value * powerFactor)
            else ->
                power.toDouble() / voltage.value // Для DC
        }
    }
}
