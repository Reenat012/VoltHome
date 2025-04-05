import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ru.mugalimov.volthome.ui.navigation.BottomNavItem
import ru.mugalimov.volthome.ui.navigation.MainBottomNavBar
import ru.mugalimov.volthome.ui.navigation.MainTopAppBar
import ru.mugalimov.volthome.ui.navigation.NavGraphApp


/**
 * Основной экран приложения, содержащий:
 * - Верхнюю панель (AppBar)
 * - Нижнюю навигационную панель
 * - Контентную область с навигацией
 */
@Composable
fun MainScreen() {
    val navController = rememberNavController()

    // Получаем текущий маршрут
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Определяем нужно ли показывать нижнее меню
    val bottomNavItems = listOf(
        BottomNavItem.Rooms.route,
        BottomNavItem.Loads.route,
        BottomNavItem.Exploitation.route
    )

    val showBottomBar = bottomNavItems.any { it == currentRoute }

    Scaffold(
        topBar = { MainTopAppBar() },
        bottomBar = {
            if (showBottomBar) {
                MainBottomNavBar(navController = navController)
            }
        }
    ) { padding ->
        NavGraphApp(
            navController = navController,
            modifier = Modifier.padding(padding),
            padding = padding
        )
    }
}

