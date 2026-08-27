package ru.mugalimov.volthome.domain.policy.breaker

import ru.mugalimov.volthome.domain.model.DeviceType

/**
 * Единая матрица product-policy для выбора автомата.
 *
 * Здесь живут:
 * - поддерживаемые номиналы
 * - floor по типам групп
 * - правила кривых
 * - базовые cable defaults
 *
 * Важно:
 * - это пока policy только для breaker selection;
 * - кабель здесь остаётся в простом product-default режиме.
 */
object BreakerPolicyDefaults {

    // Поддерживаемый набор номиналов автомата
    val supportedNominalsA: List<Int> = listOf(10, 16, 20, 25, 32, 40, 50, 63, 80, 100, 125, 160)

    val maxSupportedNominalA: Int
        get() = supportedNominalsA.last()

    /**
     * Floor по типу группы.
     *
     * Это и есть формализация текущих product-default semantics.
     */
    fun floorByDeviceType(deviceType: DeviceType): Int = when (deviceType) {
        DeviceType.LIGHTING -> 10
        DeviceType.SOCKET -> 16
        DeviceType.HEAVY_DUTY -> 16
        DeviceType.OVEN -> 20
        DeviceType.AIR_CONDITIONER -> 20
        DeviceType.WASHING_MACHINE -> 16
        DeviceType.DISHWASHER -> 16
        DeviceType.WATER_HEATER -> 16
        DeviceType.ELECTRIC_STOVE -> 25
        DeviceType.OTHER -> 10
    }
}
