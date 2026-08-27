package ru.mugalimov.volthome.ui.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.core.theme.VhColors
import ru.mugalimov.volthome.core.theme.VhDimens
import ru.mugalimov.volthome.ui.model.LocalUserPlan

// ---------- Bottom bar модели (как у тебя было) ----------

sealed class BottomNavItem(
    val title: String,
    val icon: ImageVector,
    val route: String,
    val proFeature: Boolean = false
) {
    data object Rooms : BottomNavItem("Комнаты", Icons.Outlined.SpaceDashboard, Screens.RoomsList.route)
    data object Loads : BottomNavItem("Нагрузки", Icons.Default.Speed, Screens.LoadsScreen.route)
    data object Explication : BottomNavItem("Линии", Icons.AutoMirrored.Outlined.ListAlt, Screens.ExplicationScreen.route)
    data object Panel : BottomNavItem(
        "Щит",
        Icons.Rounded.GridView,
        Screens.PanelVisualizationScreen.route,
        proFeature = true
    )
}

val mainBottomNavItems: List<BottomNavItem> = listOf(
    BottomNavItem.Rooms,
    BottomNavItem.Loads,
    BottomNavItem.Explication,
    BottomNavItem.Panel
)

// ---------- Drawer state через CompositionLocal ----------

val LocalDrawerState = staticCompositionLocalOf<DrawerState?> { null }

// ---------- Bottom bar (как у тебя было) ----------

@Composable
fun MainBottomNavBar(navController: NavHostController) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val t = VhColors.tokens
    val panelVisualizationAllowed = LocalUserPlan.current.capabilities.panelVisualization

    NavigationBar(
        containerColor = t.surface,
        tonalElevation = 0.dp,
        modifier = Modifier.heightIn(min = 68.dp)
    ) {
        mainBottomNavItems.forEach { item ->
            NavigationBarItem(
                icon = {
                    if (item.proFeature && !panelVisualizationAllowed) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = t.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Text("PRO")
                                }
                            }
                        ) {
                            Icon(item.icon, item.title)
                        }
                    } else {
                        Icon(item.icon, item.title)
                    }
                },
                label = { Text(item.title) },
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        // Нижние разделы находятся в одном плоском графе. Сохранение стека
                        // стартовой вкладки здесь восстанавливало лежащие поверх «Комнат»
                        // экраны (например, «Линии») вместо самой вкладки.
                        popUpTo(Screens.RoomsList.route) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = t.primary,
                    selectedTextColor = t.primary,
                    indicatorColor = t.primarySurface,
                    unselectedIconColor = t.textMuted,
                    unselectedTextColor = t.textSecondary
                )
            )
        }
    }
}
