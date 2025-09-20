package ru.mugalimov.volthome.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import ru.mugalimov.volthome.ui.components.AppScaffoldWithDrawer
import ru.mugalimov.volthome.ui.model.ProjectUi
import ru.mugalimov.volthome.ui.model.UserProfileUi
import ru.mugalimov.volthome.ui.viewmodel.AuthViewModel
import ru.mugalimov.volthome.ui.viewmodel.ProjectsViewModel

@Composable
fun MainApp(
    rootNavController: NavHostController,
    authVm: AuthViewModel
) {
    val appNavController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    // Проекты
    val projectsVm: ProjectsViewModel = hiltViewModel()
    val projectsFlow: Flow<List<ProjectUi>> = projectsVm.projectsUi

    // Профиль — пока без привязки к внутренней модели AuthViewModel,
    // чтобы не падать на st.user.*
    // Когда дашь структуру State.Success/профиля — подставлю реальные поля.
    val profileFlow: Flow<UserProfileUi?> = emptyFlow()

    AppScaffoldWithDrawer(
        title = "VoltHome",
        profileFlow = profileFlow,
        projectsFlow = projectsFlow,
        drawerState = drawerState,
        onLogout = { /* TODO: hook real logout from AuthViewModel */ },
        onSelectProject = { id -> projectsVm.selectProject(id) },
        onCreateProject = { projectsVm.createNewProject() },
        onOpenSettings = { appNavController.navigate(Screens.SettingsScreen.route) },
        onOpenProfile = { appNavController.navigate(Screens.ProfileScreen.route) },
        onOpenSubscription = { /* TODO: экран подписки */ },
        onOpenAbout = { rootNavController.navigate(Screens.AboutScreen.route) },
        bottomBar = {
            // Если добавишь нижнюю навигацию — помести сюда.
        }
    ) {
        NavGraphApp(
            navController = appNavController,
            modifier = Modifier.fillMaxSize(),
            padding = PaddingValues(),
            showOnboarding = { /* no-op пока */ },
            authVm = authVm
        )
    }
}