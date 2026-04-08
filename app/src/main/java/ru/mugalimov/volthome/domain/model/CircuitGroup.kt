package ru.mugalimov.volthome.domain.model

/**
 * Доменная группа расчётной системы.
 *
 * Важно:
 * - nominalCurrent = canonical current группы;
 * - это же значение используется:
 *   1) как persisted расчётный ток группы,
 *   2) как weight for phase balancing,
 *   3) как источник для decision log распределения фаз.
 *
 * Отдельного "скрытого веса" для балансировки у группы быть не должно.
 */
// Каждая подгруппа содержит полную информацию для карточки
data class CircuitGroup(
    val groupId: Long = 0, // Добавляем ID
    val groupNumber: Int,        // Уникальный номер группы
    val roomName: String,        // Название комнаты
    val roomId: Long,            // ID комнаты
    val groupType: DeviceType,   // Тип группы (освещение, розетки и т.д.)
    val devices: List<Device>,   // Устройства в группе

    // Расчетные параметры
    val nominalCurrent: Double,  // Текущий расчётный ток группы (А) в AUTO path
    val installedPowerW: Int,
    val circuitBreaker: Int,     // Номинал автомата (А)
    val cableSection: Double,    // Сечение кабеля (мм²)
    val breakerType: String,     // Тип автомата ("B", "C", "D")

    // Параметры безопасности
    val rcdRequired: Boolean,    // Требуется ли УЗО
    val rcdCurrent: Int = 30,     // Ток утечки для УЗО (мА)

    // Фазы
    val phase: Phase = Phase.A
) {
    /**
     * Явный алиас для мест, где группе нужен "вес" в балансировке.
     * Это не отдельное поле, а просто читаемое имя того же canonical current.
     */
    val balancingWeightA: Double
        get() = nominalCurrent
}