package ru.mugalimov.volthome.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.mugalimov.volthome.ui.model.LocalUserPlan
import ru.mugalimov.volthome.ui.model.ProjectUi
import ru.mugalimov.volthome.ui.model.UserProfileUi
import ru.mugalimov.volthome.ui.navigation.MainBottomNavBar
import ru.mugalimov.volthome.ui.navigation.NavGraphApp
import ru.mugalimov.volthome.ui.navigation.Screens
import ru.mugalimov.volthome.ui.screens.start_drawer.AppScaffoldWithDrawer
import ru.mugalimov.volthome.ui.viewmodel.AuthViewModel
import ru.mugalimov.volthome.ui.viewmodel.ProfileViewModel
import ru.mugalimov.volthome.ui.viewmodel.ProjectsViewModel
import ru.mugalimov.volthome.ui.viewmodel.UserPlanViewModel

@Composable
fun MainApp(
    rootNavController: NavHostController,
    authVm: AuthViewModel
) {
    val appNavController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    // --- проекты
    val projectsVm: ProjectsViewModel = hiltViewModel()
    val projectsFlow: Flow<List<ProjectUi>> = projectsVm.projectsUi
    val appBarTitle = projectsVm.activeProjectTitle.collectAsState().value

    // --- профиль
    val profileVm: ProfileViewModel = hiltViewModel()

    // --- тариф (free/pro)
    val userPlanVm: UserPlanViewModel = hiltViewModel()
    val userPlan = userPlanVm.plan.collectAsState().value

    // Подтягиваем профиль, когда авторизация успешна
    LaunchedEffect(authVm.state.collectAsState().value) {
        val s = authVm.state.value
        if (s is AuthViewModel.State.Success) {
            profileVm.refresh()
        }
    }

    val profileFlow: Flow<UserProfileUi?> =
        profileVm.state.map { st ->
            when (st) {
                is ProfileViewModel.UiState.Data -> {
                    val me = st.me
                    UserProfileUi(
                        name = me.displayName.ifBlank { "Пользователь" }, // safety
                        email = me.email,
                        avatarUrl = me.avatarUrl,
                        subscriptionStatus = me.plan
                    )
                }

                else -> null
            }
        }

    // --- экраны, где показываем нижнюю панель ---
    val bottomRoutes = remember {
        setOf(
            Screens.RoomsList.route,
            Screens.LoadsScreen.route,
            Screens.ExplicationScreen.route
        )
    }
    val navBackStackEntry = appNavController.currentBackStackEntryAsState().value
    val currentRoute = navBackStackEntry?.destination?.route

    // Пробрасываем тариф в UI через CompositionLocal
    CompositionLocalProvider(
        LocalUserPlan provides userPlan
    ) {
        AppScaffoldWithDrawer(
            title = appBarTitle, // ← было "VoltHome"
            profileFlow = profileFlow,
            projectsFlow = projectsFlow,
            drawerState = drawerState,
            onLogout = { authVm.signOut() },
            onSelectProject = { id ->
                // 1) меняем активный проект (данные/синк)
                projectsVm.selectProject(id)
                // 2) гарантированно уходим в RoomsList
                appNavController.navigate(Screens.RoomsList.route) {
                    popUpTo(appNavController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            onCreateProject = {
                projectsVm.createNewProject()
                appNavController.navigate(Screens.RoomsList.route) {
                    popUpTo(appNavController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            onOpenSettings = {
                appNavController.navigate(Screens.SettingsScreen.route) { launchSingleTop = true }
            },
            onOpenProfile = {
                appNavController.navigate(Screens.ProfileScreen.route) { launchSingleTop = true }
            },
            onOpenSubscription = { /* TODO: экран подписки */ },
            onOpenAbout = {
                rootNavController.navigate(Screens.AboutScreen.route) { launchSingleTop = true }
            },
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
                padding = PaddingValues(),
                showOnboarding = { /* no-op */ },
                authVm = authVm
            )
        }
    }
}