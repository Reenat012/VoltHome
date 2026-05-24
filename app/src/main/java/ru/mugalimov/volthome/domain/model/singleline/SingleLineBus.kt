package ru.mugalimov.volthome.domain.model.singleline

import ru.mugalimov.volthome.domain.model.Phase

/**
 * Шина на однолинейной схеме.
 *
 * Для MVP N и PE являются визуальными элементами схемы,
 * а не отдельной расчётной моделью.
 */
data class SingleLineBus(
    val type: SingleLineBusType,
    val label: String,
    val phase: Phase? = null
)

/**
 * Тип шины для визуализации.
 */
enum class SingleLineBusType {
    PHASE,
    NEUTRAL,
    PROTECTIVE_EARTH
}