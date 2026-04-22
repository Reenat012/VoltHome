package ru.mugalimov.volthome.ui.screens.start_drawer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import ru.mugalimov.volthome.ui.model.ProjectUi
import ru.mugalimov.volthome.ui.model.UserProfileUi
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingScreen

/**
 * Обёртка для интеграции Drawer в основной контейнер.
 *
 * ВАЖНО:
 * - content ДОЛЖЕН быть ПОСЛЕДНИМ параметром, чтобы работал trailing lambda:
 *   AppScaffoldWithDrawer(...) { ... }
 * - Диалог Save/Cancel/Stay управляется из MainApp и пробрасывается вниз до StartDrawer.
 */
@Composable
fun AppScaffoldWithDrawer(
    title: String,
    profileFlow: Flow<UserProfileUi?> = emptyFlow(),
    projectsFlow: Flow<List<ProjectUi>> = emptyFlow(),
    drawerState: DrawerState = rememberDrawerState(initialValue = DrawerValue.Closed),

    onLogout: () -> Unit,
    onSelectProject: (String) -> Unit,
    onCreateProject: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSubscription: () -> Unit,
    onOpenAbout: () -> Unit,

    onRenameProject: (id: String, newName: String) -> Unit = { _, _ -> },
    onDeleteProject: (id: String) -> Unit = {},

    // ✅ Единый триггер: вся логика в MainApp (enter/guard/save/cancel)
    onManualModeClick: (() -> Unit)? = null,

    // ✅ Визуальный стейт чипа (AUTO/MANUAL/DIRTY)
    manualChipState: ManualModeChipState = ManualModeChipState.AUTO,

    // ✅ Текущий onboarding screen для привязки anchor к чипу ручного режима
    manualChipOnboardingScreen: OnboardingScreen? = null,

    bottomBar: @Composable () -> Unit = {},

    // ✅ Диалог Save/Cancel/Stay (управляется из MainApp)
    manualExitDialogVisible: Boolean = false,
    onManualExitDialogDismiss: () -> Unit = {},
    onManualSaveClick: () -> Unit = {},
    onManualCancelClick: () -> Unit = {},

    // ✅ content ПОСЛЕДНИМ — чтобы trailing lambda работала корректно
    content: @Composable () -> Unit,
) {
    val profile by profileFlow.collectAsState(initial = null)
    val projects by projectsFlow.collectAsState(initial = emptyList())

    StartDrawer(
        title = title,
        drawerState = drawerState,
        profile = profile,
        projects = projects,
        onLogout = onLogout,
        onSelectProject = onSelectProject,
        onCreateProject = onCreateProject,
        onOpenSettings = onOpenSettings,
        onOpenProfile = onOpenProfile,
        onOpenSubscription = onOpenSubscription,
        onOpenAbout = onOpenAbout,
        onRenameProject = onRenameProject,
        onDeleteProject = onDeleteProject,
        onManualModeClick = onManualModeClick,
        manualChipState = manualChipState,
        manualChipOnboardingScreen = manualChipOnboardingScreen,
        bottomBar = bottomBar,
        manualExitDialogVisible = manualExitDialogVisible,
        onManualExitDialogDismiss = onManualExitDialogDismiss,
        onManualSaveClick = onManualSaveClick,
        onManualCancelClick = onManualCancelClick,
    ) { _ ->
        Box(Modifier.fillMaxSize()) {
            content()
        }
    }
}