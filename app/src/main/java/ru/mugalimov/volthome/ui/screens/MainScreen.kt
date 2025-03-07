import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.mugalimov.volthome.ui.navigation.BottomNavItem
import ru.mugalimov.volthome.ui.navigation.MainBottomNavBar
import ru.mugalimov.volthome.ui.navigation.MainTopAppBar
import ru.mugalimov.volthome.ui.navigation.NavGraphApp
import ru.mugalimov.volthome.ui.navigation.Screen
import ru.mugalimov.volthome.ui.screens.AddRoomScreen
import ru.mugalimov.volthome.ui.screens.ExploitationScreen
import ru.mugalimov.volthome.ui.screens.LoadsScreen
import ru.mugalimov.volthome.ui.screens.RoomsScreen


/**
 * Основной экран приложения, содержащий:
 * - Верхнюю панель (AppBar)
 * - Нижнюю навигационную панель
 * - Контентную область с навигацией
 */
@Composable
fun MainScreen() {
    // Контроллер навигации для управления переходами между экранами
    val navController = rememberNavController()

    // Состояние выбранного пункта нижнего меню
    var selectedItem by remember { mutableStateOf<BottomNavItem>(BottomNavItem.Rooms) }

    // Базовая структура экрана с использованием Scaffold
    Scaffold(
        topBar = { MainTopAppBar() },
        bottomBar = { MainBottomNavBar(selectedItem, onItemSelected = { selectedItem = it }) }
    ) { padding ->
        NavGraphApp(
            navController = navController,
            selectedItem = selectedItem,
            padding = padding
        )
    }
}

