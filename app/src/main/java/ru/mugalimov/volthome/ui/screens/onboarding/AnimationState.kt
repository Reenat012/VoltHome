package ru.mugalimov.volthome.ui.screens.onboarding

/**
 * Состояние анимации Lottie
 */
data class AnimationState(
    val isPlaying: Boolean = true,
    val speed: Float = 1f,
    val restartOnPlay: Boolean = false
)
