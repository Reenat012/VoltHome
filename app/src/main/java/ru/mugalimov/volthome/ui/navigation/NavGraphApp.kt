package ru.mugalimov.volthome.ui.navigation

import SettingsScreen
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import ru.mugalimov.volthome.ui.screens.algoritm_about.AlgorithmExplanationScreen
import ru.mugalimov.volthome.ui.screens.explication.ExplicationScreen
import ru.mugalimov.volthome.ui.screens.loads.PhaseLoadScreen
import ru.mugalimov.volthome.ui.screens.profile.ProfileScreen
import ru.mugalimov.volthome.ui.screens.room.RoomDetailScreen
import ru.mugalimov.volthome.ui.screens.rooms.RoomsScreen
import ru.mugalimov.volthome.ui.viewmodel.AuthViewModel
import ru.mugalimov.volthome.ui.viewmodel.RoomDetailViewModel

/**
 * Внутренний (main) граф. Навигацию на экран авторизации выполняет RootNavGraph,
 * он слушает состояние authVm. Поэтому здесь просто пробрасываем authVm вниз.
 */
@Composable
fun NavGraphApp(
    navController: NavHostController,
    modifier: Modifier,
    padding: PaddingValues,
    showOnboarding: () -> Unit,
    authVm: AuthViewModel
) {
    NavHost(
        navController = navController,
        startDestination = BottomNavItem.Rooms.route,
        modifier = Modifier.padding(padding)
    ) {
        // Комнаты
        composable(route = Screens.RoomsList.route) {
            RoomsScreen(
                onAddRoom = { /* handled inside RoomsScreen */ },
                onClickRoom = { roomId ->
                    navController.navigate(Screens.RoomDetailScreen.createRoute(roomId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        // Нагрузки
        composable(route = Screens.LoadsScreen.route) { PhaseLoadScreen() }

        // Экспликация
        composable(route = BottomNavItem.Exploitation.route) { ExplicationScreen() }

        // Детали комнаты
        composable(
            route = Screens.RoomDetailScreen.route,
            arguments = listOf(navArgument("roomId") { type = NavType.LongType })
        ) { backStackEntry ->
            val vm: RoomDetailViewModel = hiltViewModel(backStackEntry)
            RoomDetailScreen(vm = vm, onBack = { navController.popBackStack() })
        }

        // Настройки
        composable(Screens.SettingsScreen.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onShowOnboarding = showOnboarding
            )
        }

        // Объяснение алгоритма
        composable(Screens.AlgorithmExplanationScreen.route) {
            AlgorithmExplanationScreen(navController)
        }

        // Фазная нагрузка
        composable(Screens.PhaseLoadScreen.route) { PhaseLoadScreen() }

        // Профиль — сюда передаём общий authVm
        composable(Screens.ProfileScreen.route) {
            ProfileScreen(
                authVm = authVm,
                onBack = { navController.popBackStack() }
            )
        }
    }
}