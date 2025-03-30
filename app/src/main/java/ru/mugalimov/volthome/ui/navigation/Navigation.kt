package ru.mugalimov.volthome.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

/**
 * Модель для элементов нижней навигационной панели.
 * @param title Отображаемое название пункта меню
 * @param icon Иконка из Material Icons
 */
sealed class BottomNavItem(
    val title: String,
    val icon: ImageVector,
    val route: String // У каждого пункта меню свой адрес, уникальный идентификатор экрана (как URL)
) {
    data object Rooms : BottomNavItem("Комнаты", Icons.Default.Home, "rooms")
    data object Loads : BottomNavItem("Нагрузки", Icons.Default.Speed, "loads")
    data object Exploitation : BottomNavItem("Экспликация", Icons.Default.List, "exploitation")
}


/**
 * Верхняя панель приложения (AppBar).
 * Использует Material Design 3 стили и цветовую схему.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopAppBar() {
    TopAppBar(
        title = { Text("VoltHome") },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

/**
 * Нижняя навигационная панель.
 * @param selectedItem Выбранный в данный момент пункт меню
 * @param onItemSelected Обработчик выбора пункта меню
 */
//@Composable
//fun MainBottomNavBar(
//    selectedItem: BottomNavItem,
//    navController: NavHostController, // Добавляем navController
//) {
//    NavigationBar(
//        containerColor = MaterialTheme.colorScheme.surfaceVariant
//    ) {
//        listOf(
//            BottomNavItem.Rooms,
//            BottomNavItem.Loads,
//            BottomNavItem.Exploitation
//        ).forEach { item ->
//            NavigationBarItem(
//                icon = { Icon(item.icon, contentDescription = item.title) },
//                label = { Text(item.title) },
//                selected = selectedItem == item,
//                onClick = {  navController.navigate(item.route) { // Используем базовый маршрут
//                    popUpTo(navController.graph.startDestinationId)
//                    launchSingleTop = true
//                } },
//                colors = NavigationBarItemDefaults.colors(
//                    selectedIconColor = MaterialTheme.colorScheme.primary,
//                    selectedTextColor = MaterialTheme.colorScheme.primary,
//                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
//                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
//                )
//            )
//        }
//    }
//}

@Composable
fun MainBottomNavBar(navController: NavHostController) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        listOf(
            BottomNavItem.Rooms,
            BottomNavItem.Loads,
            BottomNavItem.Exploitation
        ).forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, item.title) },
                label = { Text(item.title) },
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}