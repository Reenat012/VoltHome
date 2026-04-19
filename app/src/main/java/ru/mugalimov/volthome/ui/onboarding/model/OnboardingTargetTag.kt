package ru.mugalimov.volthome.ui.onboarding.model

/**
 * Централизованный список target tags для onboarding.
 *
 * В commit 2 tags уже централизуем, даже если commit 3 только начнёт
 * массово размечать реальные target-элементы.
 */
enum class OnboardingTargetTag(
    val rawTag: String
) {
    // Projects
    PROJECTS_ADD_BUTTON("projects_add_button"),
    PROJECTS_FIRST_ITEM("projects_first_item"),

    // Rooms
    ROOMS_ADD_FAB("rooms_add_fab"),
    ROOMS_FIRST_CARD("rooms_first_card"),

    // Add room
    ADD_ROOM_NAME_FIELD("add_room_name_field"),
    ADD_ROOM_CONFIRM_BUTTON("add_room_confirm_button"),

    // Room details
    ROOM_DETAILS_ADD_DEVICES_FAB("room_details_add_devices_fab"),
    ROOM_DETAILS_FIRST_DEVICE("room_details_first_device"),

    // Loads
    LOADS_DONUT_CHART("loads_donut_chart"),
    LOADS_FIRST_GROUP("loads_first_group"),

    // Explication
    EXPLICATION_SHIELD_OVERVIEW("explication_shield_overview"),
    EXPLICATION_UNASSIGNED_BLOCK("explication_unassigned_block"),
    EXPLICATION_PDF_FAB("explication_pdf_fab")
}