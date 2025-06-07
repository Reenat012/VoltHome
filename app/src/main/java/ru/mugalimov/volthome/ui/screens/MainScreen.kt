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
import androidx.navigation.NavHostController
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
// Новый композейбл для основного приложения
@Composable
fun MainApp(rootNavController: NavHostController) {
    Scaffold(
        topBar = { MainTopAppBar(rootNavController = rootNavController) },
        bottomBar = { /* Здесь будет нижняя панель */ }
    ) { innerPadding ->
        MainScreen(
            rootNavController = rootNavController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

// Обновленный MainScreen
@Composable
fun MainScreen(
    rootNavController: NavHostController,
    modifier: Modifier = Modifier
) {
    val mainNavController = rememberNavController()

    // Получаем текущий маршрут
    val navBackStackEntry by mainNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Определяем нужно ли показывать нижнее меню
    val bottomNavItems = listOf(
        BottomNavItem.Rooms.route,
        BottomNavItem.Loads.route,
        BottomNavItem.Exploitation.route
    )

    val showBottomBar = bottomNavItems.any { it == currentRoute }

    Scaffold(
        topBar = { /* Можем оставить пустым, так как верхняя панель в MainApp */ },
        bottomBar = {
            if (showBottomBar) {
                MainBottomNavBar(navController = mainNavController)
            }
        }
    ) { padding ->
        NavGraphApp(
            navController = mainNavController,
            modifier = modifier.padding(padding),
            padding = padding
        )
    }
}

