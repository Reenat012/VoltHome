package ru.mugalimov.volthome.domain.use_case

import ru.mugalimov.volthome.data.repository.DeviceRepository
import ru.mugalimov.volthome.domain.model.Device
import javax.inject.Inject

// Считаем суммарную мощность всех устройств в списке
class CalcSumPowerDevices @Inject constructor(
    private val deviceRepository: DeviceRepository
) {
    fun execute(devices: List<Device>) : Int {
        return devices.sumOf { it.power }
    }
}