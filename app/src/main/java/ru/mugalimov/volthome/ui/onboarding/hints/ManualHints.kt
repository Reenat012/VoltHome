package ru.mugalimov.volthome.ui.onboarding.hints

import ru.mugalimov.volthome.ui.onboarding.model.ExplicationOnboardingFacts
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingHintId
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingScreen
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingTargetTag

/**
 * Подсказки для ручного режима.
 *
 * Важно:
 * - manual hint не показываем при любом blocking state;
 * - target — постоянный legal banner, а не случайный ephemeral UI.
 */
object ManualHints {

    val EXPLICATION_MANUAL_MODE_INFO = AdvancedHintSpec(
        hintId = OnboardingHintId.EXPLICATION_MANUAL_MODE_INFO,
        screen = OnboardingScreen.EXPLICATION,
        targetTag = OnboardingTargetTag.EXPLICATION_MANUAL_LEGAL_BANNER,
        priority = 120,
        title = "Ручной режим — это твоя ответственность",
        body = "Здесь ты вручную меняешь структуру щита. Приложение помогает считать, но не заменяет инженерную проверку и соответствие нормам."
    )

    fun forExplication(facts: ExplicationOnboardingFacts): AdvancedHintSpec? {
        if (!facts.isSuccess) return null
        if (!facts.manualModeActive) return null
        if (facts.hasBlockingState) return null
        return EXPLICATION_MANUAL_MODE_INFO
    }
}