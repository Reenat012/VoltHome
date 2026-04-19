package ru.mugalimov.volthome.ui.onboarding.model

/**
 * Канонические факты для Rooms onboarding.
 *
 * Источник:
 * - только RoomViewModel
 */
data class RoomsOnboardingFacts(
    val roomsCount: Int = 0,
    val isLoading: Boolean = true
)