package ru.mugalimov.volthome.ui.onboarding.model

/**
 * Канонические факты для advanced onboarding на экране экспликации.
 *
 * Важно:
 * - success/readiness берём только из success-state;
 * - pdfAvailable не угадываем, а выводим из реального UI state;
 * - blocking states формализованы явно.
 */
data class ExplicationOnboardingFacts(
    val isLoading: Boolean = true,
    val isSuccess: Boolean = false,
    val groupsCount: Int = 0,
    val manualModeActive: Boolean = false,
    val unassignedCount: Int = 0,
    val pdfAvailable: Boolean = false,

    // Прогресс линейного manual onboarding.
    val manualIntroShown: Boolean = false,
    val longPressShown: Boolean = false,
    val saveShown: Boolean = false,
    val unassignedShown: Boolean = false,
    val overviewShown: Boolean = false,
    val singleLineShown: Boolean = false,
    val panelShown: Boolean = false,
    val pdfShown: Boolean = false,

    // Блокирующие состояния.
    val dragInProgress: Boolean = false,
    val moveStateActive: Boolean = false,
    val bottomSheetOpen: Boolean = false,
    val confirmDialogOpen: Boolean = false,
    val pdfExportFlowActive: Boolean = false,
    val modalOverlayActive: Boolean = false,
) {
    val hasBlockingState: Boolean
        get() = dragInProgress ||
                moveStateActive ||
                bottomSheetOpen ||
                confirmDialogOpen ||
                pdfExportFlowActive ||
                modalOverlayActive
}
