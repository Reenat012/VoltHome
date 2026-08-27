package ru.mugalimov.volthome.domain.use_case

import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.DeviceType

/**
 * Каноническая политика нагрузки набора устройств проекта.
 *
 * Элемент каталога `SOCKET + !requiresSocketConnection` описывает точку общей
 * розеточной сети, а не отдельный прибор мощностью 2,2 кВт. Несколько таких
 * точек в одном помещении образуют одну расчётную розеточную нагрузку: берётся
 * наиболее тяжёлая из заданных точек. Иначе количество розеток искусственно
 * умножало мощность объекта и могло приводить к «рекомендации» ввода 160 А.
 *
 * Реальные приборы, в том числе подключаемые через розетку, по-прежнему
 * суммируются. Точки разных помещений не объединяются.
 */
object CircuitLoadCalculator {

    data class Entry(
        val roomId: Long?,
        val deviceType: DeviceType,
        val requiresSocketConnection: Boolean,
        val requiresDedicatedCircuit: Boolean,
        val loadInput: LoadInput
    )

    fun calculate(devices: Iterable<Device>): GroupLoad =
        CurrentCalculator.calculateGroupLoad(
            effectiveDevices(devices).map { it.toLoadInput() }
        )

    /**
     * Возвращает фактические вклады в расчёт. Это публичная часть политики,
     * чтобы пояснения в UI не рассказывали про сумму там, где ядро применило
     * расчётную нагрузку общей розеточной линии.
     */
    fun effectiveDevices(devices: Iterable<Device>): List<Device> {
        val snapshot = devices.toList()
        val regular = snapshot.filterNot(::isGeneralSocketPoint)
        val socketAllowances = snapshot
            .filter(::isGeneralSocketPoint)
            .groupBy(Device::roomId)
            .values
            .mapNotNull { roomPoints ->
                roomPoints.maxByOrNull { device ->
                    CurrentCalculator.calculateDeviceLoad(device.toLoadInput()).calculatedCurrentA
                }
            }
        return regular + socketAllowances
    }

    private fun Device.toLoadInput(): LoadInput =
        LoadInput(
            powerW = power.toDouble(),
            voltage = voltage.value.toDouble(),
            powerFactor = powerFactor,
            demandRatio = demandRatio,
            voltageType = voltage.type,
            label = name
        )

    fun calculateEntries(entries: Iterable<Entry>): GroupLoad {
        val snapshot = entries.toList()
        if (snapshot.isEmpty()) return CurrentCalculator.calculateGroupLoad(emptyList())

        val regular = snapshot.filterNot(::isGeneralSocketPoint)
        val socketAllowances = snapshot
            .filter(::isGeneralSocketPoint)
            .groupBy { it.roomId }
            .values
            .mapNotNull { roomPoints ->
                roomPoints.maxByOrNull { entry ->
                    CurrentCalculator.calculateDeviceLoad(entry.loadInput).calculatedCurrentA
                }
            }

        return CurrentCalculator.calculateGroupLoad(
            (regular + socketAllowances).map(Entry::loadInput)
        )
    }

    fun isGeneralSocketPoint(device: Device): Boolean =
        device.deviceType == DeviceType.SOCKET &&
            !device.requiresSocketConnection &&
            !device.requiresDedicatedCircuit

    private fun isGeneralSocketPoint(entry: Entry): Boolean =
        entry.deviceType == DeviceType.SOCKET &&
            !entry.requiresSocketConnection &&
            !entry.requiresDedicatedCircuit
}
