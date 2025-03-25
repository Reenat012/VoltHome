import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    // Контроллер навигации для управления переходами между экранами
    // "мозг" навигации, запоминает куда мы ходили
    val navController = rememberNavController()

    // Опеределяем текущий выбранный пункт на основе маршрута
    // Следим за текущим этажом (экран + параметры)
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    // Состояние выбранного пункта нижнего меню
    // Определяем выбранную кнопку по текущему этажу
    val selectedItem = when (navBackStackEntry?.destination?.route) {
        BottomNavItem.Rooms.route -> BottomNavItem.Rooms
        BottomNavItem.Loads.route -> BottomNavItem.Loads
        BottomNavItem.Exploitation.route -> BottomNavItem.Exploitation
        else -> BottomNavItem.Rooms

    }

    // Базовая структура экрана с использованием Scaffold
    Scaffold(
        topBar = { MainTopAppBar() },
        bottomBar = {
            MainBottomNavBar(selectedItem, onItemSelected = { item ->
                // Навигация очистки стека
                // При нажатии кнопки говорим лифту куда ехать
                navController.navigate(item.route) {
                    // Очищаем историю переходов (как кнопка "Домой" в лифте)
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true // Не создавать новый экран, если уже на нем
                    restoreState = true // Восстанавливаем скролл и т.д.
                }
            })
        }
    ) { padding ->
        // Место где отображаются экраны
        // карта всех "этажей"
        NavGraphApp(
            navController = navController,
            selectedItem = selectedItem,
            padding = padding
        )
    }
}

