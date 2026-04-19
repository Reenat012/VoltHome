package ru.mugalimov.volthome.ui.onboarding.model

/**
 * Runtime-модель активной подсказки.
 *
 * В commit 4 сюда добавлены title/body,
 * чтобы overlay показывал реальный пользовательский текст, а не debug-id.
 */
data class ActiveHint(
    val hintId: OnboardingHintId,
    val screen: OnboardingScreen,
    val targetTag: OnboardingTargetTag?,
    val title: String,
    val body: String,
    val activatedAtMillis: Long
)