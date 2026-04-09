package ru.mugalimov.volthome.domain.policy.cable

import ru.mugalimov.volthome.domain.model.DeviceType

/**
 * Единая матрица product-policy для выбора кабеля.
 *
 * Важно:
 * - кабель выбирается только после автомата;
 * - здесь нет выбора автомата;
 * - selector ниже получает уже выбранный breaker.
 */
object CablePolicyDefaults {

    // Поддерживаемые продуктом сечения.
    val supportedSectionsMm2: List<Double> = listOf(1.5, 2.5, 4.0, 6.0, 10.0, 16.0)

    /**
     * Нормативный минимальный пол по автомату.
     *
     * Это floor, ниже которого продукт опускаться не должен.
     */
    fun normativeFloorByBreaker(breakerA: Int): Double = when {
        breakerA <= 10 -> 1.5
        breakerA <= 20 -> 2.5
        breakerA <= 25 -> 4.0
        breakerA <= 32 -> 6.0
        breakerA <= 50 -> 10.0
        else -> 16.0
    }

    /**
     * Product-default.
     *
     * Пока он совпадает с floor по автомату.
     * Это оставляет место для будущего расхождения:
     * - normative floor
     * - product default
     * - advanced override
     */
    fun productDefaultByBreaker(breakerA: Int): Double = normativeFloorByBreaker(breakerA)

    /**
     * Спец-правила по типу нагрузки пока не ужесточаем.
     * Но точку расширения оставляем здесь.
     */
    fun typeAwareFloorByBreaker(
        breakerA: Int,
        deviceType: DeviceType
    ): Double {
        val base = normativeFloorByBreaker(breakerA)

        return when (deviceType) {
            // Пока не ужесточаем относительно breaker floor.
            DeviceType.LIGHTING,
            DeviceType.SOCKET,
            DeviceType.HEAVY_DUTY,
            DeviceType.AIR_CONDITIONER,
            DeviceType.ELECTRIC_STOVE,
            DeviceType.OVEN,
            DeviceType.WASHING_MACHINE,
            DeviceType.DISHWASHER,
            DeviceType.WATER_HEATER,
            DeviceType.OTHER -> base
        }
    }
}