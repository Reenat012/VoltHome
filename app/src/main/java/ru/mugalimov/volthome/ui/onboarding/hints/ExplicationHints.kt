package ru.mugalimov.volthome.ui.onboarding.hints

import ru.mugalimov.volthome.ui.onboarding.model.ExplicationOnboardingFacts
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingHintId
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingScreen
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingTargetTag

/**
 * Подсказки для экрана экспликации.
 */
object ExplicationHints {

    val EXPLICATION_UNASSIGNED_DEVICES = AdvancedHintSpec(
        hintId = OnboardingHintId.EXPLICATION_UNASSIGNED_DEVICES,
        screen = OnboardingScreen.EXPLICATION,
        targetTag = OnboardingTargetTag.EXPLICATION_UNASSIGNED_BLOCK,
        priority = 115,
        title = "Нераспределённые устройства требуют решения",
        body = "Эти устройства пока не попали в группы. Их можно вернуть в группы вручную или пересобрать структуру проекта."
    )

    val EXPLICATION_OVERVIEW = AdvancedHintSpec(
        hintId = OnboardingHintId.EXPLICATION_OVERVIEW,
        screen = OnboardingScreen.EXPLICATION,
        targetTag = OnboardingTargetTag.EXPLICATION_SHIELD_OVERVIEW,
        priority = 110,
        title = "Здесь сводка по щиту",
        body = "На этой карточке собраны ключевые параметры: вводной аппарат, группы и итоговые расчётные значения."
    )

    fun forUnassigned(facts: ExplicationOnboardingFacts): AdvancedHintSpec? {
        if (!facts.isSuccess) return null
        if (!facts.manualModeActive) return null
        if (facts.unassignedCount <= 0) return null
        if (facts.hasBlockingState) return null
        return EXPLICATION_UNASSIGNED_DEVICES
    }

    fun forOverview(facts: ExplicationOnboardingFacts): AdvancedHintSpec? {
        if (!facts.isSuccess) return null
        if (facts.groupsCount <= 0) return null
        if (facts.hasBlockingState) return null
        return EXPLICATION_OVERVIEW
    }
}