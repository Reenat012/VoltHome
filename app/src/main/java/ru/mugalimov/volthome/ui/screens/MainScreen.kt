import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ru.mugalimov.volthome.ui.navigation.BottomNavItem
import ru.mugalimov.volthome.ui.navigation.MainBottomNavBar
import ru.mugalimov.volthome.ui.navigation.MainTopAppBar
import ru.mugalimov.volthome.ui.navigation.NavGraphApp
import ru.mugalimov.volthome.ui.navigation.Screens
import ru.mugalimov.volthome.ui.viewmodel.AuthViewModel

/**
 * Основной контейнер приложения (top bar + bottom bar + контент-граф).
 * Сюда пробрасываем общий AuthViewModel из RootNavGraph.
 */
@Composable
fun MainApp(
    rootNavController: NavHostController,
    authVm: AuthViewModel
) {
    val mainNavController = rememberNavController()

    Scaffold(
        topBar = {
            MainTopAppBar(
                rootNavController = rootNavController,
                mainNavController = mainNavController
            )
        },
        bottomBar = {
            val navBackStackEntry = mainNavController.currentBackStackEntryAsState().value
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
        NavGraphApp(
            navController = mainNavController,
            modifier = Modifier.padding(innerPadding),
            padding = innerPadding,
            showOnboarding = {
                rootNavController.navigate(Screens.OnBoardingScreen.route) {
                    popUpTo(Screens.MainApp.route) { inclusive = true }
                }
            },
            authVm = authVm // пробрасываем дальше
        )
    }
}