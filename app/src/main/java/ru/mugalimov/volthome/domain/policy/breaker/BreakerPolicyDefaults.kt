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
    val supportedNominalsA: List<Int> = listOf(10, 16, 20, 25, 32, 40, 50, 63)

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

    /**
     * Product-default сечение по номиналу автомата.
     *
     * Пока не делаем отдельный сложный cable policy.
     */
    fun defaultCableSectionByBreaker(breakerA: Int): Double = when {
        breakerA <= 10 -> 1.5
        breakerA <= 20 -> 2.5
        breakerA <= 25 -> 4.0
        breakerA <= 32 -> 6.0
        breakerA <= 50 -> 10.0
        else -> 16.0
    }
}