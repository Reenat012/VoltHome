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
import ru.mugalimov.volthome.ui.navigation.Screens


/**
 * Основной экран приложения, содержащий:
 * - Верхнюю панель (AppBar)
 * - Нижнюю навигационную панель
 * - Контентную область с навигацией
 */
// Новый композейбл для основного приложения
// MainApp.kt
@Composable
fun MainApp(rootNavController: NavHostController) {
    val mainNavController = rememberNavController()

    Scaffold(
        topBar = {
            MainTopAppBar(
                rootNavController = rootNavController,
                mainNavController = mainNavController
            )
        },
        bottomBar = {
            // Определяем, нужно ли показывать нижнюю панель
            val navBackStackEntry by mainNavController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            val bottomNavItems = listOf(
                BottomNavItem.Rooms.route,
                BottomNavItem.Loads.route,
                BottomNavItem.Exploitation.route
            )

            if (bottomNavItems.any { it == currentRoute }) {
                MainBottomNavBar(navController = mainNavController)
            }
        }
    ) { innerPadding ->
        // Убираем MainScreen и переносим его функционал сюда
        NavGraphApp(
            navController = mainNavController,
            modifier = Modifier.padding(innerPadding),
            padding = innerPadding,
            showOnboarding = {
                rootNavController.navigate(Screens.OnBoardingScreen.route) {
                    popUpTo(Screens.MainApp.route) { inclusive = true }
                }
            }
        )
    }
}

