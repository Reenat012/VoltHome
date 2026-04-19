package ru.mugalimov.volthome.ui.onboarding.model

/**
 * Минимальная runtime-модель активной подсказки.
 *
 * В commit 2 сюда добавлен targetTag:
 * - host должен понимать, к какому target пытаться привязаться
 * - если targetTag == null или target невалиден, используется fallback по центру
 */
data class ActiveHint(
    val hintId: OnboardingHintId,
    val screen: OnboardingScreen,
    val targetTag: OnboardingTargetTag?,
    val activatedAtMillis: Long
)