package ru.mugalimov.volthome.domain.model

/**
 * Профиль линии, выбранный policy-слоем.
 *
 * Важно:
 * - breakerRating / breakerType теперь приходят из единого selector-а;
 * - whyBreakerSelected — объяснение, почему policy выбрала именно этот автомат.
 *
 * Пока persistence не трогаем, поэтому explanation живёт только в домене/UI.
 */
data class GroupProfile(
    val maxCurrent: Double,          // Максимальный расчётный ток для группы (А)
    val breakerRating: Int,          // Номинал автомата (А)
    val cableSection: Double,        // Сечение кабеля (мм²)
    val breakerType: String,         // Тип автомата ("B", "C", "D")
    val whyBreakerSelected: LineSelectionReason? = null
) {
    // Ток с учетом запаса 20% (правило 80% для длительных нагрузок)
    val maxCurrentWithReserve = maxCurrent * 0.8
}