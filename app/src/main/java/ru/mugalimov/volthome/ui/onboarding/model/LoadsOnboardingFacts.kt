package ru.mugalimov.volthome.ui.onboarding.model

import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadMode

/**
 * Канонические факты для Loads onboarding.
 *
 * Источник:
 * - только PhaseLoadViewModel.
 */
data class LoadsOnboardingFacts(
    val groupsCount: Int = 0,
    val phaseMode: PhaseMode = PhaseMode.THREE,
    val phaseLoadMode: PhaseLoadMode = PhaseLoadMode.AUTO,
    val isLoading: Boolean = true
)