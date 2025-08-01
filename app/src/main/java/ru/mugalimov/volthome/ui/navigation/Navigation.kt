package ru.mugalimov.volthome.ui.navigation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import ru.mugalimov.volthome.ui.utilities.AlgorithmInfoButton

import ru.mugalimov.volthome.ui.utilities.TelegramButton


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
fun MainTopAppBar(
    rootNavController: NavHostController,
    mainNavController: NavHostController
) {
    TopAppBar(
        title = { Text("VoltHome") },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        actions = {
            AlgorithmInfoButton(mainNavController) // Показываться этот экран описания работы алгоритма будет здесь
            IconButton(onClick = {
                // Используем mainNavController для внутренних экранов
                mainNavController.navigate(Screens.SettingsScreen.route)
            }) {
                Icon(Icons.Default.Settings, "Настройки")
            }
//            IconButton(onClick = { rootNavController.navigate(Screens.AboutScreen.route) }) {
//                Icon(Icons.Default.Info, "О программе")
//            }
        }
    )
}

/**
 * Нижняя навигационная панель.
 * @param selectedItem Выбранный в данный момент пункт меню
 * @param onItemSelected Обработчик выбора пункта меню
 */

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

