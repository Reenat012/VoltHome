package ru.mugalimov.volthome.ui.components

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

/**
 * Обёртка для интеграции Drawer в основной контейнер.
 * Теперь поддерживает [bottomBar].
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
    bottomBar: @Composable () -> Unit = {},
    content: @Composable () -> Unit
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
        bottomBar = bottomBar
    ) { _ ->
        Box(Modifier.fillMaxSize()) {
            content()
        }
    }
}