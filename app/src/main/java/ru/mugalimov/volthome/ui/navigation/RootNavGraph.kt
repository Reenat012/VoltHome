package ru.mugalimov.volthome.ui.navigation

import AboutScreen
import MainApp
import WelcomeScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yandex.authsdk.YandexAuthSdk
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.collectLatest
import ru.mugalimov.volthome.ui.screens.auth.AuthScreen
import ru.mugalimov.volthome.ui.viewmodel.AuthViewModel

/**
 * Корневой NavHost. Слушает общий AuthViewModel и переключает граф.
 */
@Composable
fun RootNavGraph(
    sdk: YandexAuthSdk,
) {
    val rootNavController = rememberNavController()
    // ВАЖНО: это «общая» VM, её же передаём вниз
    val authVm: AuthViewModel = hiltViewModel()

    LaunchedEffect(Unit) { authVm.bootstrap() }

    LaunchedEffect(Unit) {
        authVm.state.collectLatest { state ->
            when (state) {
                is AuthViewModel.State.Success -> {
                    rootNavController.navigate(Screens.MainApp.route) {
                        popUpTo(Screens.WelcomeScreen.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                is AuthViewModel.State.Idle,
                is AuthViewModel.State.Error -> {
                    rootNavController.navigate(Screens.WelcomeScreen.route) {
                        popUpTo(0) { inclusive = true }
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

private fun NavGraphBuilder.authGraph(
    sdk: YandexAuthSdk,
    authVm: AuthViewModel
) {
    composable(Screens.WelcomeScreen.route) {
        AuthScreen(sdk = sdk) {
            // по коллбеку можно добустить, но Root уже слушает стейт
            authVm.bootstrap()
        }
    }
}

private fun NavGraphBuilder.mainGraph(
    rootNavController: NavHostController,
    authVm: AuthViewModel
) {
    composable(Screens.MainApp.route) {
        MainApp(
            rootNavController = rootNavController,
            authVm = authVm
        )
    }
}

private fun NavGraphBuilder.aboutGraph(
    rootNavController: NavHostController
) {
    composable(Screens.AboutScreen.route) {
        AboutScreen(onBack = { rootNavController.popBackStack() })
    }
}