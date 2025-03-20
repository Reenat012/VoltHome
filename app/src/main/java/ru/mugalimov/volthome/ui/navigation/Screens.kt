package ru.mugalimov.volthome.ui.navigation

/**
Навигационные маршруты
 */
sealed class Screens(val route: String) {
    data object RoomsList : Screens("rooms_list")
    data object AddRoom : Screens("add_room")
    data object LoadsScreen : Screens("loads_screen")
    data object ExploitationScreen : Screens("exploitation_screen")
    data object RoomDetailScreen: Screens("room_detail/{roomId}") {
        // Функция для генерации пути с roomId.
        // Параметр roomId подставляется в маршрут.
        fun createRoute(roomId: Int) = "room_detail/$roomId"
    }
    data object AddDeviceScreen: Screens("add_device/{roomId}") {
        // Функция для генерации пути с roomId.
        // Параметр roomId подставляется в маршрут.
        fun createRoute(roomId: Int) = "add_device/$roomId"
    }
    data object DeviceList: Screens(route = "device_list")
}