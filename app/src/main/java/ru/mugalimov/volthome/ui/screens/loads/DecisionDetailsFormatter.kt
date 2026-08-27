package ru.mugalimov.volthome.ui.screens.loads

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import ru.mugalimov.volthome.domain.model.DistributionDecision
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.ui.format.UiTextFormat

/**
 * Форматтер уровня A/B.
 * UI не должен знать про формулы, пороги и округления.
 */
object DecisionExplanationFormatter {

    private const val MIN_DELTA_I_THRESHOLD_A = 0.3   // |ΔI| < 0.3A => "без изменений"
    private const val CURRENT_ROUND_STEP_A = 0.1      // округление токов до 0.1A

    data class AB(
        val levelA_title: String,
        val levelA_metric: String,
        val levelB_reason: String,
        val levelB_before: String,
        val levelB_after: String
    )

    fun formatAB(d: DistributionDecision): AB {
        val before = normalizeTriplet(d.phaseCurrentsBefore)
        val after = normalizeTriplet(d.phaseCurrentsAfter)

        val imbalanceBefore = imbalance(before)
        val imbalanceAfter = imbalance(after)
        val deltaI = roundToStep(imbalanceAfter - imbalanceBefore, CURRENT_ROUND_STEP_A)

        val titleA = "Почему фаза ${d.chosenPhase.name}"
        val metricA = buildBalanceMetric(deltaI)

        val reasonB = "Причина: " + buildReason(before, d.chosenPhase, deltaI)

        val beforeLine = "До: " + formatTriplet(before)
        val afterLine = "После: " + formatTriplet(after)

        return AB(
            levelA_title = titleA,
            levelA_metric = metricA,
            levelB_reason = reasonB,
            levelB_before = beforeLine,
            levelB_after = afterLine
        )
    }

    private fun buildBalanceMetric(deltaI: Double): String {
        return if (abs(deltaI) < MIN_DELTA_I_THRESHOLD_A) {
            "Баланс: без изменений"
        } else {
            val status = if (deltaI < 0.0) "лучше" else "хуже"
            val signed = formatSigned(deltaI)
            "Баланс: $status (разница $signed${UiTextFormat.NBSP}А)"
        }
    }

    private fun buildReason(
        before: Map<Phase, Double>,
        chosen: Phase,
        deltaI: Double
    ): String {
        val minBefore = minOf(before[Phase.A] ?: 0.0, before[Phase.B] ?: 0.0, before[Phase.C] ?: 0.0)
        val chosenBefore = before[chosen] ?: 0.0
        val chosenWasAmongMin = abs(chosenBefore - minBefore) < 0.05 // “почти минимум”

        return when {
            chosenWasAmongMin -> "эта фаза была менее загружена"
            deltaI < -MIN_DELTA_I_THRESHOLD_A -> "так распределение стало равномернее"
            else -> "другие варианты давали больший перекос"
        }
    }

    private fun normalizeTriplet(src: Map<Phase, Double>): Map<Phase, Double> = mapOf(
        Phase.A to (src[Phase.A] ?: 0.0),
        Phase.B to (src[Phase.B] ?: 0.0),
        Phase.C to (src[Phase.C] ?: 0.0)
    )

    private fun imbalance(m: Map<Phase, Double>): Double {
        val a = m[Phase.A] ?: 0.0
        val b = m[Phase.B] ?: 0.0
        val c = m[Phase.C] ?: 0.0
        val mx = max(a, max(b, c))
        val mn = min(a, min(b, c))
        return mx - mn
    }

    private fun formatTriplet(m: Map<Phase, Double>): String {
        fun fmt(v: Double): String {
            val rounded = roundToStep(v, CURRENT_ROUND_STEP_A)
            val s = UiTextFormat.decimal(rounded)
            return if (s == "0,0") "0" else s
        }
        return "A ${fmt(m[Phase.A] ?: 0.0)}  B ${fmt(m[Phase.B] ?: 0.0)}  C ${fmt(m[Phase.C] ?: 0.0)}"
    }

    private fun formatSigned(v: Double): String {
        val absStr = UiTextFormat.decimal(abs(v)).let { if (it == "0,0") "0" else it }
        return if (v >= 0.0) "+$absStr" else "-$absStr"
    }

    private fun roundToStep(value: Double, step: Double): Double {
        if (step <= 0.0) return value
        return round(value / step) * step
    }
}
