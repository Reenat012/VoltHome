package ru.mugalimov.volthome.ui.onboarding

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * EntryPoint для runtime-доступа к onboarding singleton-объектам
 * из composable helper-ов и верхнего host-контейнера.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface OnboardingRuntimeEntryPoint {
    fun onboardingCoordinator(): OnboardingCoordinator
    fun uiAnchorRegistry(): UiAnchorRegistry
}