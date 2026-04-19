package ru.mugalimov.volthome.ui.onboarding.model

import ru.mugalimov.volthome.domain.model.PhaseMode

/**
 * Канонические факты для Loads onboarding.
 *
 * Источник:
 * - только PhaseLoadViewModel
 */
data class LoadsOnboardingFacts(
    val groupsCount: Int = 0,
    val phaseMode: PhaseMode = PhaseMode.THREE,
    val isLoading: Boolean = true
)