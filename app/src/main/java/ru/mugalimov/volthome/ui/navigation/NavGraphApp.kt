package ru.mugalimov.volthome.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import ru.mugalimov.volthome.ui.screens.about.SettingsScreen
import ru.mugalimov.volthome.ui.screens.algoritm_about.AlgorithmExplanationScreen
import ru.mugalimov.volthome.ui.screens.explication.ExplicationScreen
import ru.mugalimov.volthome.ui.screens.loads.PhaseLoadScreen
import ru.mugalimov.volthome.ui.screens.profile.ProfileScreen
import ru.mugalimov.volthome.ui.screens.room.RoomDetailScreen
import ru.mugalimov.volthome.ui.screens.rooms.RoomsScreen
import ru.mugalimov.volthome.ui.screens.subscription.VoltHomeProScreen
import ru.mugalimov.volthome.ui.viewmodel.AuthViewModel
import ru.mugalimov.volthome.ui.viewmodel.ProjectsViewModel
import ru.mugalimov.volthome.ui.viewmodel.RoomDetailViewModel

/**
 * Внутренний (main) граф приложения.
 * Гарантируем:
 *  - старт всегда RoomsList,
 *  - при смене активного проекта уходим в RoomsList даже с Profile/RoomDetail.
 */
@Composable
fun NavGraphApp(
    navController: NavHostController,
    modifier: Modifier,
    padding: PaddingValues,
    showOnboarding: () -> Unit,
    authVm: AuthViewModel
) {
    // Одноразовый reset стека на входе в граф
    var didReset by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!didReset) {
            runCatching {
                navController.navigate(Screens.RoomsList.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        inclusive = true
                    }
                    launchSingleTop = true
                }
            }
            didReset = true
        }
    }

    // 🔁 Реакция на смену активного проекта — жёсткий переход в RoomsList
    val projectsVm: ProjectsViewModel = hiltViewModel()
    val activeProjectId by projectsVm.activeProjectId.collectAsState(initial = null)

    // Роуты нижней навигации, куда можно оставаться
    val bottomRoutes = remember {
        setOf(
            Screens.RoomsList.route,
            Screens.LoadsScreen.route,
            Screens.ExplicationScreen.route
        )
    }

    LaunchedEffect(activeProjectId) {
        if (activeProjectId != null) {
            val current = navController.currentDestination?.route
            if (current !in bottomRoutes) {
                // Сначала пытаемся вернуться к RoomsList, если он есть в стеке
                val popped = navController.popBackStack(Screens.RoomsList.route, inclusive = false)
                if (!popped) {
                    // Если в стеке нет — переходим и чистим до старта графа
                    navController.navigate(Screens.RoomsList.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screens.RoomsList.route,
        modifier = modifier // паддинги уже применены наверху
    ) {
        composable(route = Screens.RoomsList.route) {
            RoomsScreen(
                onAddRoom = { /* ... */ },
                onClickRoom = { roomId ->
                    navController.navigate(Screens.RoomDetailScreen.createRoute(roomId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(route = Screens.LoadsScreen.route) { PhaseLoadScreen() }
        composable(route = Screens.ExplicationScreen.route) { ExplicationScreen() }

        composable(
            route = Screens.RoomDetailScreen.route,
            arguments = listOf(navArgument("roomId") { type = NavType.LongType })
        ) {
            val vm: RoomDetailViewModel = hiltViewModel(it)
            RoomDetailScreen(vm = vm, onBack = { navController.popBackStack() })
        }

        composable(Screens.SettingsScreen.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onShowOnboarding = showOnboarding
            )
        }

        composable(Screens.AlgorithmExplanationScreen.route) {
            AlgorithmExplanationScreen(navController)
        }

        composable(Screens.PhaseLoadScreen.route) { PhaseLoadScreen() }

        composable(Screens.ProfileScreen.route) {
            ProfileScreen(authVm = authVm, onBack = { navController.popBackStack() })
        }

        composable(Screens.SubscriptionScreen.route) {
            VoltHomeProScreen()
        }
    }
}