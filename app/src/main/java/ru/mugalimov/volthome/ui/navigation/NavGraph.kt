package ru.mugalimov.volthome.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import ru.mugalimov.volthome.ui.screens.AddRoomScreen
import ru.mugalimov.volthome.ui.screens.ExploitationScreen
import ru.mugalimov.volthome.ui.screens.LoadsScreen
import ru.mugalimov.volthome.ui.screens.RoomsScreen

/**
 * Контентная область приложения с навигационным графом.
 * @param navController Контроллер навигации
 * @param selectedItem Выбранный пункт нижнего меню
 * @param padding Отступы от панелей Scaffold
 */
@Composable
fun NavGraphApp(
    navController: NavHostController,
    selectedItem: BottomNavItem,
    padding: PaddingValues
) {
    // Навигационный граф приложения
    NavHost(
        navController = navController,
        startDestination = Screen.RoomsList.route, //домашний экран
        modifier = Modifier.padding(padding)
    ) {
        // Маршруты для раздела "Комнаты"
        composable(Screen.RoomsList.route) {
            RoomsScreen(
                //маршрут добавления новой комнаты
                onAddRoom = { navController.navigate(Screen.AddRoom.route) },
                //маршрут для перехода в комнату
                onClickRoom = { roomId ->
                    navController.navigate(
                        Screen.RoomDetail.createRoute(roomId)

                    )
                })
        }
        composable(Screen.AddRoom.route) {
            AddRoomScreen(onBack = { navController.popBackStack() })
        }


        // Маршруты для других разделов
        composable(Screen.LoadsScreen.route) { LoadsScreen() }
        composable(Screen.ExploitationScreen.route) { ExploitationScreen() }
    }
}