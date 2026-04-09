package ru.mugalimov.volthome.domain.model

/**
 * Объяснение, почему policy выбрала именно такой автомат.
 *
 * Это domain-level explanation:
 * - без UI-зависимостей
 * - без persistence
 * - пригодно для тестов, логов и будущего UI
 */
data class LineSelectionReason(
    val deviceType: DeviceType,
    val nominalCurrentA: Double,

    // Минимум по типу группы (floor)
    val floorBreakerA: Int,

    // Требуемый ток после округления вверх и применения floor
    val requiredBreakerA: Int,

    // Итог policy
    val selectedBreakerA: Int,
    val selectedCurve: String,

    // Техническое объяснение
    val curveRule: String,
    val productRule: String
)