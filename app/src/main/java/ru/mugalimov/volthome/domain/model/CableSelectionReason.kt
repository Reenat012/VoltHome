package ru.mugalimov.volthome.domain.model

/**
 * Объяснение, почему policy выбрала именно такое сечение кабеля.
 *
 * Важно:
 * - это domain/runtime explanation;
 * - persistence пока не трогаем;
 * - причина выбора кабеля должна опираться на уже выбранный автомат.
 */
data class CableSelectionReason(
    val breakerA: Int,
    val deviceType: DeviceType,

    // Минимум текущей продуктовой матрицы (не нормативный расчёт)
    val minimumProductSectionMm2: Double,

    // Product-default, который продукт выбирает для данного автомата
    val productDefaultSectionMm2: Double,

    // Итог policy
    val selectedSectionMm2: Double,

    // Техническое объяснение
    val selectionRule: String,
    val productRule: String
)
