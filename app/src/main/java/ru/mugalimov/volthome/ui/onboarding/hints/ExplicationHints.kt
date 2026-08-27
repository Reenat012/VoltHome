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
        body = "Эти устройства пока не попали в группы. Их можно распределить вручную по группам или оставить для дальнейшего решения."
    )

    val EXPLICATION_OVERVIEW = AdvancedHintSpec(
        hintId = OnboardingHintId.EXPLICATION_OVERVIEW,
        screen = OnboardingScreen.EXPLICATION,
        targetTag = OnboardingTargetTag.EXPLICATION_SHIELD_OVERVIEW,
        priority = 110,
        title = "Здесь сводка по щиту",
        body = "На этой карточке собраны ключевые параметры: вводной аппарат, группы и итоговые расчётные значения."
    )

    val EXPLICATION_OPEN_PANEL = AdvancedHintSpec(
        hintId = OnboardingHintId.EXPLICATION_OPEN_PANEL,
        screen = OnboardingScreen.EXPLICATION,
        targetTag = OnboardingTargetTag.EXPLICATION_PANEL_CARD,
        priority = 112,
        title = "Щит сформирован",
        body = "Откройте компоновку по DIN-рейкам, выберите конкретные аппараты и уточните стоимость проекта."
    )

    val EXPLICATION_SINGLE_LINE = AdvancedHintSpec(
        hintId = OnboardingHintId.EXPLICATION_SINGLE_LINE,
        screen = OnboardingScreen.EXPLICATION,
        targetTag = OnboardingTargetTag.EXPLICATION_SINGLE_LINE_BUTTON,
        priority = 105,
        title = "Постройте однолинейную схему",
        body = "Приложение свяжет ввод, фазы, аппараты защиты, кабели и группы в одной схеме без ручной отрисовки."
    )

    fun forUnassigned(facts: ExplicationOnboardingFacts): AdvancedHintSpec? {
        if (!facts.isSuccess) return null
        if (!facts.manualModeActive) return null
        if (facts.unassignedCount <= 0) return null
        if (facts.unassignedShown) return null
        if (facts.hasBlockingState) return null
        if (!facts.manualIntroShown || !facts.longPressShown || !facts.saveShown) return null
        return EXPLICATION_UNASSIGNED_DEVICES
    }

    fun forOverview(facts: ExplicationOnboardingFacts): AdvancedHintSpec? {
        if (!facts.isSuccess) return null
        if (facts.groupsCount <= 0) return null
        if (facts.overviewShown) return null
        if (facts.hasBlockingState) return null
        if (facts.manualModeActive && (!facts.manualIntroShown || !facts.longPressShown || !facts.saveShown)) {
            return null
        }
        return EXPLICATION_OVERVIEW
    }

    fun forPanel(facts: ExplicationOnboardingFacts): AdvancedHintSpec? {
        if (!facts.isSuccess || facts.groupsCount <= 0) return null
        if (facts.panelShown || facts.hasBlockingState) return null
        if (facts.manualModeActive && (!facts.manualIntroShown || !facts.longPressShown || !facts.saveShown)) {
            return null
        }
        return EXPLICATION_OPEN_PANEL
    }

    fun forSingleLine(facts: ExplicationOnboardingFacts): AdvancedHintSpec? {
        if (!facts.isSuccess || facts.groupsCount <= 0) return null
        if (!facts.overviewShown || facts.singleLineShown) return null
        if (facts.hasBlockingState) return null
        return EXPLICATION_SINGLE_LINE
    }
}
