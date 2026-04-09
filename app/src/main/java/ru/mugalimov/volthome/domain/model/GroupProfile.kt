package ru.mugalimov.volthome.domain.model

/**
 * Профиль линии, выбранный policy-слоем.
 *
 * Важно:
 * - breakerRating / breakerType приходят из единого line policy;
 * - cableSection тоже приходит из того же line policy;
 * - whyBreakerSelected / whyCableSelected живут только в домене и runtime,
 *   persistence пока не трогаем.
 */
data class GroupProfile(
    val maxCurrent: Double,          // Максимальный расчётный ток для группы (А)
    val breakerRating: Int,          // Номинал автомата (А)
    val cableSection: Double,        // Сечение кабеля (мм²)
    val breakerType: String,         // Тип автомата ("B", "C", "D")
    val whyBreakerSelected: LineSelectionReason? = null,
    val whyCableSelected: CableSelectionReason? = null
) {
    // Правило 80% оставляем как вспомогательное вычисление.
    val maxCurrentWithReserve: Double
        get() = maxCurrent * 0.8
}