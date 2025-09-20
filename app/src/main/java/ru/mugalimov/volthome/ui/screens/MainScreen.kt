package ru.mugalimov.volthome.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.ui.components.AppScaffoldWithDrawer
import ru.mugalimov.volthome.ui.viewmodel.AuthViewModel

/**
 * Основной контейнер приложения:
 * Drawer + AppBar + BottomBar (там, где нужно) + app-NavHost.
 */
@Composable
fun MainApp(
    rootNavController: androidx.navigation.NavHostController,
    authVm: AuthViewModel
) {
    val mainNavController = rememberNavController()
    val scope = rememberCoroutineScope()


    // Текущий route внутри app-графа
    val navBackStackEntry = mainNavController.currentBackStackEntryAsState().value
    val currentRoute = navBackStackEntry?.destination?.route
    val bottomRootRoutes = setOf(
        BottomNavItem.Rooms.route,
        BottomNavItem.Loads.route,
        BottomNavItem.Exploitation.route
    )

    AppScaffoldWithDrawer(
        title = "VoltHome",
        onLogout = {
            // ВАЖНО: именно выходим из аккаунта через общую VM.
            // RootNavGraph слушает state и сам уводит на экран авторизации.
            scope.launch {
                authVm.signOut()
            }
        },
        onSelectProject = { /* открыть проект */ },
        onCreateProject = {
            mainNavController.navigate(Screens.RoomsList.route) {
                launchSingleTop = true
                restoreState = true
                popUpTo(mainNavController.graph.findStartDestination().id) { saveState = true }
            }
        },
        onOpenSettings = {
            mainNavController.navigate(Screens.SettingsScreen.route) {
                launchSingleTop = true
                restoreState = true
                popUpTo(mainNavController.graph.findStartDestination().id) { saveState = true }
            }
        },
        onOpenProfile = {
            mainNavController.navigate(Screens.ProfileScreen.route) {
                launchSingleTop = true
                restoreState = true
                popUpTo(mainNavController.graph.findStartDestination().id) { saveState = true }
            }
        },
        onOpenSubscription = {
            mainNavController.navigate(Screens.SettingsScreen.route) {
                launchSingleTop = true
                restoreState = true
                popUpTo(mainNavController.graph.findStartDestination().id) { saveState = true }
            }
        },
        onOpenAbout = {
            // "О приложении" — часть root-графа
            rootNavController.navigate(Screens.AboutScreen.route) { launchSingleTop = true }
        },
        bottomBar = {
            if (currentRoute in bottomRootRoutes) {
                MainBottomNavBar(navController = mainNavController)
            }
        }
    ) {
        // Внутренний app-граф (Rooms/Loads/Exploitation, Settings, Profile и пр.)
        NavGraphApp(
            navController = mainNavController,
            modifier = Modifier,
            padding = androidx.compose.foundation.layout.PaddingValues(),
            showOnboarding = {
                rootNavController.navigate(Screens.OnBoardingScreen.route) {
                    popUpTo(Screens.MainApp.route) { inclusive = true }
                }
            },
            authVm = authVm
        )
    }
}