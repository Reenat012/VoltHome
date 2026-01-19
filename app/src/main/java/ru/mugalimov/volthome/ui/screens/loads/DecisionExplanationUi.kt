package ru.mugalimov.volthome.ui.screens.loads

import ru.mugalimov.volthome.domain.model.DistributionDecision


/**
 * Контракт объяснения распределения (A/B/C).
 * A и B — всегда для Free/PRO.
 * C — "аудит решения" (платная ценность), строится из списка событий.
 *
 * Важно:
 * - В levelC_auditText НЕ должно быть повторов A/B:
 *   "Почему фаза", "Причина:", "До:", "После:", "ΔI" — запрещены.
 * - Никакого decision.note в UI.
 */
data class DecisionExplanationUi(
    // A
    val levelA_title: String,
    val levelA_metric: String,

    // B
    val levelB_reason: String,
    val levelB_before: String,
    val levelB_after: String,

    // C (PRO)
    val levelC_title: String,
    val levelC_auditText: List<String>
)

/**
 * A/B строим из одного decision (последнее состояние).
 */
fun DistributionDecision.toDecisionExplanationUi(): DecisionExplanationUi {
    val ab = DecisionExplanationFormatter.formatAB(this)

    return DecisionExplanationUi(
        // A
        levelA_title = ab.levelA_title,
        levelA_metric = ab.levelA_metric,

        // B
        levelB_reason = ab.levelB_reason,
        levelB_before = ab.levelB_before,
        levelB_after = ab.levelB_after,

        // C (пока заглушка: реальный текст отдаст DecisionDetailsFormatter)
        levelC_title = "Аудит решения",
        levelC_auditText = emptyList()
    )
}