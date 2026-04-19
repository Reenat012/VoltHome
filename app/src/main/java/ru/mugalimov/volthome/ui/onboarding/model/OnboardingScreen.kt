package ru.mugalimov.volthome.ui.onboarding.model

/**
 * Централизованный список экранов для onboarding-системы.
 *
 * Важно:
 * - не использовать route-строки как "магические" идентификаторы
 * - не плодить screen id по месту
 */
enum class OnboardingScreen {
    PROJECTS,
    ROOMS,
    ADD_ROOM_SHEET,
    ROOM_DETAILS,
    LOADS,
    EXPLICATION
}