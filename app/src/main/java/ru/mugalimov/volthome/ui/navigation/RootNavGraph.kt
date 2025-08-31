package ru.mugalimov.volthome.ui.navigation

import AboutScreen
import MainApp
import SettingsScreen
import WelcomeScreen
import android.content.SharedPreferences
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ru.mugalimov.volthome.ui.screens.onboarding.OnboardingScreen


// RootNavGraph.kt
@Composable
fun RootNavGraph(
    startDestination: String,
    onFirstLaunchCompleted: () -> Unit
) {
    val rootNavController = rememberNavController()

    NavHost(
        navController = rootNavController,
        startDestination = startDestination
    ) {
        composable(Screens.WelcomeScreen.route) {
            WelcomeScreen(
                onContinue = {
                    onFirstLaunchCompleted()
//                    rootNavController.navigate(Screens.OnBoardingScreen.route) {
//                        popUpTo(0)
//                    }
                }
            )
        }

        // Запуск анимации
//        composable(Screens.OnBoardingScreen.route) {
//            OnboardingScreen(
//                onComplete = {
//                    // Всегда переходим на главный экран
//                    rootNavController.navigate(Screens.MainApp.route) {
//                        popUpTo(0)
//                    }
//
//                    // Вызываем завершение ТОЛЬКО при первом запуске
//                    if (rootNavController.previousBackStackEntry?.destination?.route != Screens.MainApp.route) {
//                        onOnboardingCompleted()
//                    }
//                },
//                animationResources = listOf(
//                    "lottie/1.json",
//                    "lottie/2.json",
//                    "lottie/3.json",
//                    "lottie/4.json",
//                    "lottie/5.json"
//                )
//            )
//        }

        composable(Screens.MainApp.route) {
            MainApp(rootNavController = rootNavController)
        }

        composable(Screens.AboutScreen.route) {
            AboutScreen(onBack = { rootNavController.popBackStack() })
        }
    }
}

