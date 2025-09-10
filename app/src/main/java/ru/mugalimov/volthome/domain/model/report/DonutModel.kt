package ru.mugalimov.volthome.domain.model.report

import ru.mugalimov.volthome.domain.model.Phase

sealed interface DonutModel {
    /** 3-ф: распределение по фазам (значения в амперах) */
    data class PhaseDistribution(
        val valuesA: Map<Phase, Double>
    ) : DonutModel

    /** 1-ф: загрузка вводного автомата (в амперах) */
    data class IncomerLoad(
        val usedA: Double,
        val limitA: Double
    ) : DonutModel
}
