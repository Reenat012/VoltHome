package ru.mugalimov.volthome.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import ru.mugalimov.volthome.ui.screens.start_drawer.AppScaffoldWithDrawer
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

    val projectsVm: ProjectsViewModel = hiltViewModel()
    val projectsFlow: Flow<List<ProjectUi>> = projectsVm.projectsUi
    val profileFlow: Flow<UserProfileUi?> = emptyFlow()

    // --- список экранов, где показываем нижнюю панель ---
    val bottomRoutes = remember {
        setOf(
            Screens.RoomsList.route,
            Screens.LoadsScreen.route,
            Screens.ExplicationScreen.route
        )
    }
    val navBackStackEntry = appNavController.currentBackStackEntryAsState().value
    val currentRoute = navBackStackEntry?.destination?.route

    AppScaffoldWithDrawer(
        title = "VoltHome",
        profileFlow = profileFlow,
        projectsFlow = projectsFlow,
        drawerState = drawerState,
        onLogout = { /* TODO: привязать logout к AuthViewModel */ },
        onSelectProject = { id -> projectsVm.selectProject(id) },
        onCreateProject = { projectsVm.createNewProject() },
        onOpenSettings = { appNavController.navigate(Screens.SettingsScreen.route) },
        onOpenProfile = { appNavController.navigate(Screens.ProfileScreen.route) },
        onOpenSubscription = { /* TODO: экран подписки */ },
        onOpenAbout = { rootNavController.navigate(Screens.AboutScreen.route) },
        onRenameProject = { id, newName -> projectsVm.renameProject(id, newName) },
        onDeleteProject = { id -> projectsVm.deleteProject(id) },
        bottomBar = {
            if (currentRoute in bottomRoutes) {
                MainBottomNavBar(navController = appNavController)
            }
        }
    ) {
        NavGraphApp(
            navController = appNavController,
            modifier = Modifier.fillMaxSize(),
            padding = PaddingValues(), // паддинги уже даёт AppScaffoldWithDrawer
            showOnboarding = { /* no-op пока */ },
            authVm = authVm
        )
    }
}