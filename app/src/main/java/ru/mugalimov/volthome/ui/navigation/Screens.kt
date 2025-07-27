package ru.mugalimov.volthome.ui.navigation

/**
Навигационные маршруты
 */
sealed class Screens(val route: String) {
    // Основные разделы нижнего меню
    data object RoomsList : Screens("rooms")
    data object LoadsScreen : Screens("loads")
    data object ExploitationScreen : Screens("exploitations")

    // Вложенные экраны
    data object AddRoom : Screens("add_room")
    data object RoomDetailScreen: Screens("room_detail/{roomId}") {
        // Функция для генерации пути с roomId.
        // Параметр roomId подставляется в маршрут.
        fun createRoute(roomId: Long) = "room_detail/$roomId"
    }
    data object AddDeviceScreen: Screens("add_device/{roomId}") {
        // Функция для генерации пути с roomId.
        // Параметр roomId подставляется в маршрут.
        fun createRoute(roomId: Long) = "add_device/$roomId"
    }
    data object DeviceList: Screens(route = "device_list")

    data object WelcomeScreen : Screens("welcome_screen")
    data object MainApp : Screens("main_app")
    data object SettingsScreen : Screens("settings")
    data object AboutScreen : Screens("about")

    data object OnBoardingScreen : Screens("onBoardingScreen")
    data object AlgorithmExplanationScreen : Screens("algorithm_explanation")
}