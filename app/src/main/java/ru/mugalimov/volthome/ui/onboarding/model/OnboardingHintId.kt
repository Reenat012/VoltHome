package ru.mugalimov.volthome.ui.onboarding.model

/**
 * Стабильные идентификаторы подсказок.
 *
 * Здесь нет текстов и UI-деталей.
 * Только ids, которые переживут:
 * - refactor,
 * - переименование текстов,
 * - смену локали.
 */
enum class OnboardingHintId(
    val storageKey: String
) {
    // Projects
    PROJECTS_ADD_FIRST_PROJECT("projects_add_first_project"),
    PROJECTS_SELECT_PROJECT("projects_select_project"),

    // Rooms
    ROOMS_ADD_FIRST_ROOM("rooms_add_first_room"),

    // Loads
    LOADS_VIEW_PHASE_BALANCE("loads_view_phase_balance"),
    LOADS_MANUAL_MODE_WARNING("loads_manual_mode_warning"),

    // Explication
    EXPLICATION_MANUAL_MODE_INFO("explication_manual_mode_info"),
    EXPLICATION_LONG_PRESS_DEVICE("explication_long_press_device"),
    EXPLICATION_SAVE_MANUAL_CHANGES("explication_save_manual_changes"),
    EXPLICATION_OVERVIEW("explication_overview"),
    EXPLICATION_UNASSIGNED_DEVICES("explication_unassigned_devices"),

    // PDF
    PDF_EXPORT_INFO("pdf_export_info")
}