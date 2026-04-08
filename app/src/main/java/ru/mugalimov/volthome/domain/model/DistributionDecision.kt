package ru.mugalimov.volthome.domain.model

/**
 * Лог решений алгоритма распределения групп по фазам.
 *
 * Важно:
 * - это не UI-модель, а доказательная модель алгоритма;
 * - должна объяснять, почему группа попала именно в эту фазу;
 * - должна быть стабильной при одинаковом input.
 */
enum class DecisionEventType {
    GREEDY_ASSIGN,
    LOCAL_OPT_MOVE
}

data class DistributionDecision(
    val groupNumber: Int,
    val groupCurrentA: Double,
    val chosenPhase: Phase,
    val phaseCurrentsBefore: Map<Phase, Double>,
    val phaseCurrentsAfter: Map<Phase, Double>,

    // Тип события алгоритма.
    val eventType: DecisionEventType = DecisionEventType.GREEDY_ASSIGN,

    // Для greedy fromPhase = null, для move — заполнено.
    val fromPhase: Phase? = null,
    val toPhase: Phase? = null,

    // Метрики перекоса.
    val imbalanceBeforeA: Double = 0.0,
    val imbalanceAfterA: Double = 0.0,

    // Техническая подпись алгоритма.
    val algorithm: String = "balanced_greedy_deterministic+local_opt_deterministic",

    // Формализованное правило tie-break.
    val tieBreakRule: String = "MIN_IMBALANCE_THEN_PHASE_ORDER_A_B_C",

    // Человекочитаемое объяснение решения.
    val note: String? = null
)