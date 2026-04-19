package ru.mugalimov.volthome.ui.onboarding.model

/**
 * Минимальная runtime-модель активной подсказки.
 *
 * Это не "декоративная" сущность, а рабочий контракт coordinator-а:
 * без неё coordinator не сможет безопасно хранить active state.
 */
data class ActiveHint(
    val hintId: OnboardingHintId,
    val screen: OnboardingScreen,
    val activatedAtMillis: Long
)