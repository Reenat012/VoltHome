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
        // Применяем коэффициент спроса к мощности ДО суммирования.
        // demandRatio в доменной Device = non-null Double.
        return devices.sumOf { (it.power * it.demandRatio).toInt() }
    }

    suspend fun calcCurrentRoom(roomId: Long): Double {
        val devices = deviceRepository.getAllDevicesByRoomId(roomId)
        return devices.sumOf { device ->
            // Важно: demandRatio и powerFactor в доменной модели Device НЕ nullable.
            // Elvis-оператор здесь запрещён типами и ломает компиляцию.
            val effectivePower = device.power.toDouble() * device.demandRatio

            // Напряжение может быть “мусорным/нулевым”, поэтому тут оставляем защиту.
            val voltage = (device.voltage.value.takeIf { it > 0 } ?: 230.0).toDouble()

            // powerFactor non-null, но защита от некорректных значений полезна (не типовая, а числовая).
            val powerFactor = device.powerFactor.coerceIn(0.8, 1.0)

            when (device.voltage.type) {
                VoltageType.AC_1PHASE -> effectivePower / (voltage * powerFactor)
                VoltageType.AC_3PHASE -> effectivePower / (1.732 * voltage * powerFactor)
                VoltageType.DC -> effectivePower / voltage
                else -> effectivePower / voltage // Для неизвестных типов
            }
        }
    }
}