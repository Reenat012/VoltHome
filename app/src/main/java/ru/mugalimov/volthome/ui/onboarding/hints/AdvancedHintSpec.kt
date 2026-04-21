package ru.mugalimov.volthome.ui.onboarding.hints

import ru.mugalimov.volthome.ui.onboarding.model.OnboardingHintId
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingScreen
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingTargetTag

/**
 * Декларативная модель advanced hint.
 *
 * Важно:
 * - тут нет side effects;
 * - тут нет доступа к coordinator/repository;
 * - здесь только спецификация.
 */
data class AdvancedHintSpec(
    val hintId: OnboardingHintId,
    val screen: OnboardingScreen,
    val targetTag: OnboardingTargetTag?,
    val priority: Int,
    val title: String,
    val body: String
)

/**
 * Единая точка выбора hint по приоритету.
 */
fun pickHighestPriorityAdvanced(candidates: List<AdvancedHintSpec>): AdvancedHintSpec? {
    return candidates.maxByOrNull { it.priority }
}