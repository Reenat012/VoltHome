package ru.mugalimov.volthome.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import ru.mugalimov.volthome.ui.screens.AddDeviceScreen
import ru.mugalimov.volthome.ui.screens.AddRoomScreen
import ru.mugalimov.volthome.ui.screens.ExploitationScreen
import ru.mugalimov.volthome.ui.screens.LoadsScreen
import ru.mugalimov.volthome.ui.screens.room.RoomDetailScreen
import ru.mugalimov.volthome.ui.screens.rooms.RoomsScreen

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
                        Screen.RoomDetailScreen.createRoute(roomId)

                    )
                }
            )
        }

        composable(
            //Определяет уникальный "адрес" экрана
            route = Screen.RoomDetailScreen.route,
            //Определяет параметры, которые можно передать на экран.
            arguments = listOf(
                //создаём аргумент с именем "roomId"
                navArgument("roomId") {
                    type = NavType.IntType //указываем, что это целое число
                    defaultValue = 0 //значение по умолчанию, если параметр не передан
                }
            )
        ) { backStackEntry -> //объект, содержащий информацию о текущем состоянии навигации.
            //получаем все аргументы экрана по roomId
            val roomId = backStackEntry.arguments?.getInt("roomId") ?: 0
            //Создаём экран RoomDetailScreen
            RoomDetailScreen(
                roomId = roomId, //Передаём ему полученный roomId
                onBack = { navController.popBackStack()},
                onClickDevice =  {navController.navigate(Screen.AddDeviceScreen.route)},
                onAddDevice = {navController.navigate(Screen.AddDeviceScreen.route)}
            )
        }

        composable(Screen.AddRoom.route) {
            AddRoomScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.AddDeviceScreen.route) {
            AddDeviceScreen(onBack = { navController.popBackStack() })
        }


        // Маршруты для других разделов
        composable(Screen.LoadsScreen.route) { LoadsScreen() }
        composable(Screen.ExploitationScreen.route) { ExploitationScreen() }
    }
}