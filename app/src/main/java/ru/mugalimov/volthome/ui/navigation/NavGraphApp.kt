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
import ru.mugalimov.volthome.ui.screens.explication.ExplicationScreen
import ru.mugalimov.volthome.ui.screens.loads.PhaseLoadScreen
import ru.mugalimov.volthome.ui.screens.room.RoomDetailScreen
import ru.mugalimov.volthome.ui.screens.rooms.RoomsScreen
import ru.mugalimov.volthome.ui.screens.algoritm_about.AlgorithmExplanationScreen
import ru.mugalimov.volthome.ui.viewmodel.RoomDetailViewModel

/**
 * Контентная область приложения с навигационным графом.
 * @param navController Контроллер навигации
 * @param selectedItem Выбранный пункт нижнего меню
 * @param padding Отступы от панелей Scaffold
 */

// карта всех "этажей"
@Composable
fun NavGraphApp(
    navController: NavHostController,
    modifier: Modifier,
    padding: PaddingValues,
    showOnboarding: () -> Unit
) {
    // Навигационный граф приложения
    // контейнер, где отображаются экраны
    NavHost(
        navController = navController,
        startDestination = BottomNavItem.Rooms.route, //домашний экран
        modifier = Modifier.padding(padding)
    ) {

        /** Основные экраны */

        // Маршруты для раздела "Комнаты"
        composable(route = Screens.RoomsList.route) {
            RoomsScreen(
                onAddRoom = { /* no-op: AddRoomSheet теперь внутри RoomsScreen */ },
                onClickRoom = { roomId ->
                    navController.navigate(Screens.RoomDetailScreen.createRoute(roomId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

//        composable(
//            route =  Screens.LoadsScreen.route
//        ) { backStackEntry ->
//            val roomId = backStackEntry.arguments?.getLong("roomId") ?: 0L
//            LoadsScreen(roomId = roomId)
//        }

        composable(route = Screens.LoadsScreen.route) {
            PhaseLoadScreen(

            )
        }

        composable(
            route = BottomNavItem.Exploitation.route,

            ) {
            ExplicationScreen()
        }


        /** Вложенные экраны */

//        composable(Screens.AddRoom.route) {
//            AddRoomScreen(onBack = { navController.popBackStack() })
//        }

        composable(
            route = Screens.RoomDetailScreen.route, // "room_detail/{roomId}"
            arguments = listOf(
                navArgument("roomId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            // Важно: VM берём с backStackEntry, чтобы SavedStateHandle получил roomId
            val vm: RoomDetailViewModel = hiltViewModel(backStackEntry)
            RoomDetailScreen(
                vm = vm,
                onBack = { navController.popBackStack() }
            )
        }

//        composable(
//            route = Screens.AddDeviceScreen.route,
//            arguments = listOf(
//                navArgument("roomId") {
//                    type = NavType.LongType
//                    defaultValue = 0L
//                }
//            )
//        ) { backStackEntry ->
//            // Извлекаем roomId из аргументов навигации
//            val roomId = backStackEntry.arguments?.getLong("roomId") ?: 0L
//            AddDeviceScreen(
//                roomId = roomId,
//                onBack = { navController.popBackStack() }
//            )
//        }

        composable(Screens.SettingsScreen.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onShowOnboarding = showOnboarding
            )
        }

        composable(Screens.AlgorithmExplanationScreen.route) {
            AlgorithmExplanationScreen(
                navController
            )
        }

        composable(Screens.PhaseLoadScreen.route) {
            PhaseLoadScreen(
            )
        }


    }
}