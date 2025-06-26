package ru.mugalimov.volthome.domain.use_case

import android.content.ContentValues.TAG
import android.util.Log
import ru.mugalimov.volthome.data.repository.DeviceRepository
import ru.mugalimov.volthome.data.repository.RoomRepository
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.domain.model.VoltageType
import javax.inject.Inject

// Считаем суммарную мощность всех устройств в списке
class CalcLoads @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val roomRepository: RoomRepository
) {
    suspend fun calPowerRoom(roomId: Long): Int {
        val rooms = roomRepository.getAllRoom()

        val allDevices = deviceRepository.getAllDevices()

        val devicesByRoom = allDevices.groupBy { it.roomId }

        val room = rooms.firstOrNull { it.id == roomId }
        val devices = room?.let { devicesByRoom[it.id] } ?: emptyList()

        return devices.sumOf { (it.power * it.demandRatio).toInt() }
    }

    suspend fun calcCurrentRoom(roomId: Long): Double {
        val devices = deviceRepository.getAllDevicesByRoomId(roomId)
        return devices.sumOf { device ->
            when (device.voltage.type) {
                VoltageType.AC_1PHASE ->
                    device.power.toDouble() / (device.voltage.value * device.powerFactor)
                VoltageType.AC_3PHASE ->
                    device.power.toDouble() / (1.732 * device.voltage.value * device.powerFactor)
                else ->
                    device.power.toDouble() / device.voltage.value // Для DC
            }
        }
    }
}