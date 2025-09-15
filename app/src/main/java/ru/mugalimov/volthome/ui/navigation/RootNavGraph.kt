package ru.mugalimov.volthome.ui.navigation

import AboutScreen
import MainApp
import WelcomeScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yandex.authsdk.YandexAuthSdk
import androidx.hilt.navigation.compose.hiltViewModel
import ru.mugalimov.volthome.ui.screens.auth.AuthScreen
import ru.mugalimov.volthome.ui.viewmodel.AuthViewModel

@Composable
fun RootNavGraph(
    startDestination: String,
    sdk: YandexAuthSdk,
    onFirstLaunchCompleted: () -> Unit
) {
    val rootNavController = rememberNavController()

    // Авторизационный гейт
    val authVm: AuthViewModel = hiltViewModel()
    val authState by authVm.state.collectAsState()

    if (authState is AuthViewModel.State.Success) {
        // Авторизован — строим основной граф
        NavHost(
            navController = rootNavController,
            startDestination = startDestination
        ) {
            composable(Screens.WelcomeScreen.route) {
                WelcomeScreen(
                    onContinue = {
                        onFirstLaunchCompleted()
                    }
                )
            }

            composable(Screens.MainApp.route) {
                MainApp(rootNavController = rootNavController)
            }

            composable(Screens.AboutScreen.route) {
                AboutScreen(onBack = { rootNavController.popBackStack() })
            }
        }
    } else {
        // Не авторизован (Idle/Loading/Error) — держим AuthScreen смонтированным,
        // чтобы не потерять колбэк ActivityResult из sdk.contract
        AuthScreen(sdk = sdk) {
            authVm.bootstrap()
        }
    }
}