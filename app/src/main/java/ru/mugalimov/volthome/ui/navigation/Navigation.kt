package ru.mugalimov.volthome.ui.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.launch

// ---------- Bottom bar модели (как у тебя было) ----------

sealed class BottomNavItem(
    val title: String,
    val icon: ImageVector,
    val route: String
) {
    data object Rooms : BottomNavItem("Комнаты", Icons.Default.Home, "rooms")
    data object Loads : BottomNavItem("Нагрузки", Icons.Default.Speed, "loads")
    data object Exploitation : BottomNavItem("Экспликация", Icons.Default.List, "exploitation")
}

// ---------- Drawer state через CompositionLocal ----------

val LocalDrawerState = staticCompositionLocalOf<DrawerState?> { null }

// ---------- TopAppBar с гамбургером и БЕЗ actions ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopAppBar(
    rootNavController: NavHostController,
    mainNavController: NavHostController
) {
    val drawerState = LocalDrawerState.current
    val scope = rememberCoroutineScope()

    TopAppBar(
        title = { Text("VoltHome") },
        navigationIcon = {
            IconButton(
                onClick = {
                    drawerState?.let { ds ->
                        scope.launch { ds.open() }
                    }
                }
            ) {
                Icon(Icons.Filled.Menu, contentDescription = "Открыть меню")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
        // ВАЖНО: никаких actions — профиль/настройки теперь в Drawer
    )
}

// ---------- Bottom bar (как у тебя было) ----------

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