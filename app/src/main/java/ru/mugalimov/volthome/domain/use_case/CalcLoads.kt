package ru.mugalimov.volthome.domain.use_case

import ru.mugalimov.volthome.data.repository.DeviceRepository
import javax.inject.Inject

/**
 * Расчёт нагрузок по комнате через canonical calculation core.
 *
 * Коммит 2:
 * - убран локальный дублирующий расчёт мощности/тока;
 * - CalcLoads теперь только агрегирует canonical group load по устройствам комнаты.
 */
class CalcLoads @Inject constructor(
    private val deviceRepository: DeviceRepository
) {
    suspend fun calPowerRoom(roomId: Long): Int {
        val devices = deviceRepository.getAllDevicesByRoomId(roomId)
        val roomLoad = CircuitLoadCalculator.calculate(devices)
        return roomLoad.calculatedPowerW.toInt()
    }

    suspend fun calcCurrentRoom(roomId: Long): Double {
        val devices = deviceRepository.getAllDevicesByRoomId(roomId)
        val roomLoad = CircuitLoadCalculator.calculate(devices)
        return roomLoad.calculatedCurrentA
    }
}
