package ru.mugalimov.volthome.domain.use_case

import ru.mugalimov.volthome.data.repository.DeviceRepository
import javax.inject.Inject

// Считаем суммарную мощность всех устройств в списке
class CalcLoads @Inject constructor(
    private val deviceRepository: DeviceRepository
) {
    suspend fun calPowerRoom(roomId: Long) : Int {
        val listDevives = deviceRepository.getAllDevicesByRoomId(roomId)

        return listDevives.sumOf { it.power * it.demandRatio.toInt() }
    }

    suspend fun calcCurrentRoom(roomId: Long, voltageDevice: Int) : Double {
        val listDevices = deviceRepository.getAllDevicesByRoomId(roomId)

        val powerRoom = calPowerRoom(roomId)
        val currentRoom = powerRoom / voltageDevice
        return currentRoom.toDouble()
    }
}