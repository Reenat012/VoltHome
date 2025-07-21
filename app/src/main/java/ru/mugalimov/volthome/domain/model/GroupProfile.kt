package ru.mugalimov.volthome.domain.model

data class GroupProfile(
    val maxCurrent: Double,      // Максимальный расчетный ток для группы (А)
    val breakerRating: Int,      // Номинал автомата (А)
    val cableSection: Double,    // Сечение кабеля (мм²)
    val breakerType: String,      // Тип автомата ("B", "C", "D")

) {
    // Ток с учетом запаса 20% (правило 80% для длительных нагрузок)
    val maxCurrentWithReserve = maxCurrent * 0.8
}