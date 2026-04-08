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
        val roomLoad = CurrentCalculator.calculateGroupLoad(
            devices.map { device ->
                LoadInput(
                    powerW = device.power.toDouble(),
                    voltage = device.voltage.value.toDouble(),
                    powerFactor = device.powerFactor,
                    demandRatio = device.demandRatio,
                    voltageType = device.voltage.type,
                    label = device.name
                )
            }
        )
        return roomLoad.calculatedPowerW.toInt()
    }

    suspend fun calcCurrentRoom(roomId: Long): Double {
        val devices = deviceRepository.getAllDevicesByRoomId(roomId)
        val roomLoad = CurrentCalculator.calculateGroupLoad(
            devices.map { device ->
                LoadInput(
                    powerW = device.power.toDouble(),
                    voltage = device.voltage.value.toDouble(),
                    powerFactor = device.powerFactor,
                    demandRatio = device.demandRatio,
                    voltageType = device.voltage.type,
                    label = device.name
                )
            }
        )
        return roomLoad.calculatedCurrentA
    }
}