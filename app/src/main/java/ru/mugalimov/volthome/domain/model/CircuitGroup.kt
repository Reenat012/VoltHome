package ru.mugalimov.volthome.domain.model

/**
 * Доменная группа расчётной системы.
 *
 * ВАЖНО (Коммит 1):
 * - поле nominalCurrent сейчас участвует как:
 *   1) итоговый AUTO group current, собранный в GroupCalculator,
 *   2) weight for phase balancing,
 *   3) одно из ключевых derived fields, сохраняемых и восстанавливаемых через persistence.
 *
 * В этом коммите семантику не меняем — только явно фиксируем её.
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
)