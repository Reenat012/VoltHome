package ru.mugalimov.volthome.ui.navigation

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import ru.mugalimov.volthome.ui.screens.room.AddDeviceScreen
import ru.mugalimov.volthome.ui.screens.ExploitationScreen
import ru.mugalimov.volthome.ui.screens.loads.LoadsScreen
import ru.mugalimov.volthome.ui.screens.room.RoomDetailScreen
import ru.mugalimov.volthome.ui.screens.rooms.AddRoomScreen
import ru.mugalimov.volthome.ui.screens.rooms.RoomsScreen
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
    padding: PaddingValues
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
        composable(
            route = BottomNavItem.Rooms.route,
        ) {
            RoomsScreen(
                //маршрут добавления новой комнаты
                onAddRoom = { navController.navigate(Screens.AddRoom.route) },
                //маршрут для перехода в комнату
                onClickRoom = { roomId ->
                    navController.navigate(
                        Screens.RoomDetailScreen.createRoute(roomId)
                    )
                }
            )
        }

        composable(
            route = "loads?roomId={roomId}",
            arguments = listOf(
                navArgument("roomId") {
                    type = NavType.LongType
                    defaultValue = 0L
                }
            )
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getLong("roomId") ?: 0L
            LoadsScreen(roomId = roomId)
        }

        composable(
            route = BottomNavItem.Exploitation.route,

            ) {
            ExploitationScreen()
        }


        /** Вложенные экраны */

        composable(Screens.AddRoom.route) {
            AddRoomScreen(onBack = { navController.popBackStack() })
        }

        composable(
            //Определяет уникальный "адрес" экрана
            route = Screens.RoomDetailScreen.route,
            //Определяет параметры, которые можно передать на экран.
            arguments = listOf(
                //создаём аргумент с именем "roomId"
                navArgument("roomId") {
                    type = NavType.LongType //указываем, что это целое число
                    defaultValue = 0L //значение по умолчанию, если параметр не передан
                }
            )
        ) { backStackEntry -> //объект, содержащий информацию о текущем состоянии навигации.

            val viewModel: RoomDetailViewModel = hiltViewModel<RoomDetailViewModel>(
                backStackEntry // Ключевое исправление!
            )

            // Извлекаем roomId из аргументов навигации
            val roomId = backStackEntry.arguments?.getLong("roomId") ?: 0L

            RoomDetailScreen(
                roomId = backStackEntry.arguments?.getLong("roomId") ?: 0L,
                onBack = { navController.popBackStack() },
                onClickDevice = { deviceId ->
                    navController.navigate("device_detail/$deviceId")
                },
                onAddDevice = {
                    // Передаем roomId в обработчик добавления устройства
                    navController.navigate(Screens.AddDeviceScreen.createRoute(roomId))
                },
                viewModel = viewModel
            )
        }

        composable(
            route = Screens.AddDeviceScreen.route,
            arguments = listOf(
                navArgument("roomId") {
                    type = NavType.LongType
                    defaultValue = 0L
                }
            )
        ) { backStackEntry ->
            // Извлекаем roomId из аргументов навигации
            val roomId = backStackEntry.arguments?.getLong("roomId") ?: 0L
            AddDeviceScreen(
                roomId = roomId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}