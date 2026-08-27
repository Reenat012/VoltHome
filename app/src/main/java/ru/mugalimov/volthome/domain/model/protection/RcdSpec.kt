package ru.mugalimov.volthome.domain.model.protection

import ru.mugalimov.volthome.domain.model.CalculationSource
import ru.mugalimov.volthome.domain.model.incomer.RcdSelectivity
import ru.mugalimov.volthome.domain.model.incomer.RcdType

enum class RcdKind {
    RCD,
    RCBO
}

/**
 * Полная спецификация дифференциальной защиты.
 *
 * ratedCurrentA nullable только для мигрированных legacy-решений, где старое
 * приложение сохраняло чувствительность, но не сохраняло номинал аппарата.
 */
data class RcdSpec(
    val kind: RcdKind,
    val ratedCurrentA: Int?,
    val leakageCurrentMa: Int,
    val type: RcdType,
    val poles: Int,
    val selectivity: RcdSelectivity,
    val source: CalculationSource
)
