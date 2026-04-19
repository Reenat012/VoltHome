package ru.mugalimov.volthome.ui.onboarding.model

import androidx.compose.ui.geometry.Rect

/**
 * Runtime-модель зарегистрированного UI-anchor.
 */
data class UiAnchor(
    val targetTag: OnboardingTargetTag,
    val screenId: OnboardingScreen,
    val bounds: Rect,
    val isAttached: Boolean
)