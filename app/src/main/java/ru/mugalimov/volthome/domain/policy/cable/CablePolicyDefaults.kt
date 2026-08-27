package ru.mugalimov.volthome.domain.policy.cable

import ru.mugalimov.volthome.domain.model.DeviceType

/**
 * Единая матрица product-policy для выбора кабеля.
 *
 * Важно:
 * - кабель выбирается только после автомата;
 * - здесь нет выбора автомата;
 * - selector ниже получает уже выбранный breaker.
 *
 * Значения являются предварительной продуктовой матрицей. Они не заменяют
 * расчёт линии по длине, материалу, способу прокладки, температуре и падению
 * напряжения — эти параметры должны быть проверены до монтажа.
 */
object CablePolicyDefaults {

    // Поддерживаемые продуктом сечения.
    val supportedSectionsMm2: List<Double> =
        listOf(1.5, 2.5, 4.0, 6.0, 10.0, 16.0, 25.0, 35.0, 50.0, 70.0)

    /**
     * Минимальное сечение, поддерживаемое текущей продуктовой матрицей.
     *
     * Это floor, ниже которого продукт опускаться не должен.
     */
    fun productFloorByBreaker(breakerA: Int): Double = when {
        breakerA <= 10 -> 1.5
        breakerA <= 20 -> 2.5
        breakerA <= 25 -> 4.0
        breakerA <= 32 -> 6.0
        breakerA <= 50 -> 10.0
        breakerA <= 63 -> 16.0
        breakerA <= 80 -> 25.0
        breakerA <= 100 -> 35.0
        breakerA <= 125 -> 50.0
        else -> 70.0
    }

    /**
     * Product-default.
     *
     * Пока он совпадает с floor по автомату.
     * Это оставляет место для будущего расхождения:
     * - product floor
     * - product default
     * - advanced override
     */
    fun productDefaultByBreaker(breakerA: Int): Double = productFloorByBreaker(breakerA)

    /**
     * Спец-правила по типу нагрузки пока не ужесточаем.
     * Но точку расширения оставляем здесь.
     */
    fun typeAwareFloorByBreaker(
        breakerA: Int,
        deviceType: DeviceType
    ): Double {
        val base = productFloorByBreaker(breakerA)

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
