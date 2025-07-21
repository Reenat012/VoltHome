package ru.mugalimov.volthome.domain.use_case

import ru.mugalimov.volthome.data.repository.DeviceRepository
import ru.mugalimov.volthome.data.repository.RoomRepository
import ru.mugalimov.volthome.domain.model.VoltageType
import javax.inject.Inject

// Считаем суммарную мощность всех устройств в списке
// Класс CalcLoads (упрощенный расчет нагрузок)
class CalcLoads @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val roomRepository: RoomRepository
) {
    suspend fun calPowerRoom(roomId: Long): Int {
        val devices = deviceRepository.getAllDevicesByRoomId(roomId)
        // Применяем коэффициент спроса к мощности ДО суммирования
        return devices.sumOf { (it.power * it.demandRatio).toInt() }
    }

    suspend fun calcCurrentRoom(roomId: Long): Double {
        val devices = deviceRepository.getAllDevicesByRoomId(roomId)
        return devices.sumOf { device ->
            // Применяем коэффициент спроса к мощности
            val effectivePower = device.power * device.demandRatio

            when (device.voltage.type) {
                VoltageType.AC_1PHASE ->
                    effectivePower / (device.voltage.value * device.powerFactor)
                VoltageType.AC_3PHASE ->
                    effectivePower / (1.732 * device.voltage.value * device.powerFactor)
                else ->
                    effectivePower / device.voltage.value
            }
        }
    }
}