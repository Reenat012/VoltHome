package ru.mugalimov.volthome.ui.navigation

/**
Навигационные маршруты
 */
sealed class Screens(val route: String) {
    // Основные разделы нижнего меню
    data object RoomsList : Screens("rooms_list")
    data object LoadsScreen : Screens("loads_screen")
    data object ExploitationScreen : Screens("exploitation_screen")

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
}