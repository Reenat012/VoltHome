package ru.mugalimov.volthome.domain.model

enum class DeviceType {
    // Стандартные группы
    LIGHTING,         // Освещение
    SOCKET,        // Розетки общего назначения
    HEAVY_DUTY,    // Мощные устройства (2200+ Вт, требует выделения)

    // Специализированные группы (по желанию, для будущего расширения)
    AIR_CONDITIONER,
    ELECTRIC_STOVE,
    OVEN,
    WASHING_MACHINE,
    DISHWASHER,
    WATER_HEATER,

    // Прочие
    OTHER
}
