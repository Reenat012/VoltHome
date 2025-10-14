package ru.mugalimov.volthome.ui.navigation

import AboutScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yandex.authsdk.YandexAuthSdk
import kotlinx.coroutines.flow.collectLatest
import ru.mugalimov.volthome.ui.screens.MainApp
import ru.mugalimov.volthome.ui.screens.auth.AuthScreen
import ru.mugalimov.volthome.ui.viewmodel.AuthViewModel

/**
 * Корневой NavHost. Drawer здесь больше НЕ рендерится, чтобы
 * исключить попытки навигации в app-дестинейшны на экране welcome.
 */
@Composable
fun RootNavGraph(
    sdk: YandexAuthSdk,
    onLogout: () -> Unit = {},
) {
    val rootNavController = rememberNavController()
    val authVm: AuthViewModel = hiltViewModel()

    LaunchedEffect(Unit) { authVm.bootstrap() }

    LaunchedEffect(Unit) {
        authVm.state.collectLatest { state ->
            when (state) {
                is AuthViewModel.State.Success -> {
                    // Переходим в App-граф. Чистим стек до старта root-графа корректно.
                    rootNavController.navigate(Screens.MainApp.route) {
                        popUpTo(rootNavController.graph.findStartDestination().id) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                }
                is AuthViewModel.State.Idle,
                is AuthViewModel.State.Error -> {
                    // Возвращаемся на welcome. Без popUpTo(0) — только через findStartDestination().
                    rootNavController.navigate(Screens.WelcomeScreen.route) {
                        popUpTo(rootNavController.graph.findStartDestination().id) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                }
                is AuthViewModel.State.Loading -> Unit
            }
        }
    }

    NavHost(
        navController = rootNavController,
        startDestination = Screens.WelcomeScreen.route
    ) {
        authGraph(sdk, authVm)
        mainGraph(rootNavController, authVm)
        aboutGraph(rootNavController)
    }
}

private fun androidx.navigation.NavGraphBuilder.authGraph(
    sdk: YandexAuthSdk,
    authVm: AuthViewModel
) {
    composable(Screens.WelcomeScreen.route) {
        AuthScreen(sdk = sdk) { authVm.bootstrap() }
    }
}

private fun androidx.navigation.NavGraphBuilder.mainGraph(
    rootNavController: NavHostController,
    authVm: AuthViewModel
) {
    composable(Screens.MainApp.route) {
        // Внутри MainApp находится Drawer + app-NavHost (NavGraphApp)
        MainApp(
            rootNavController = rootNavController,
            authVm = authVm
        )
    }
}

private fun androidx.navigation.NavGraphBuilder.aboutGraph(
    rootNavController: NavHostController
) {
    composable(Screens.AboutScreen.route) {
        AboutScreen(onBack = { rootNavController.popBackStack() })
    }
}