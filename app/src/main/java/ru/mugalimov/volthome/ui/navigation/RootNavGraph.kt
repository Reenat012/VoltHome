package ru.mugalimov.volthome.ui.navigation

import AboutScreen
import SettingsScreen
import WelcomeScreen
import android.content.SharedPreferences
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.mugalimov.volthome.ui.screens.onboarding.OnboardingScreen


// RootNavGraph.kt
@Composable
fun RootNavGraph(
    startDestination: String,
    onFirstLaunchCompleted: () -> Unit,
    onOnboardingCompleted: () -> Unit
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
                    rootNavController.navigate(Screens.OnBoardingScreen.route) {
                        popUpTo(0)
                    }
                }
            )
        }

        composable(Screens.OnBoardingScreen.route) {
            OnboardingScreen(
                onComplete = {
                    onOnboardingCompleted()
                    rootNavController.navigate(Screens.MainApp.route) {
                        popUpTo(0)
                    }
                },
                animationResources = listOf("lottie/1-2.json", "lottie/1-3.json")
            )
        }

        composable(Screens.MainApp.route) {
            MainApp(rootNavController = rootNavController)
        }

        composable(Screens.SettingsScreen.route) {
            SettingsScreen(onBack = { rootNavController.popBackStack() })
        }

        composable(Screens.AboutScreen.route) {
            AboutScreen(onBack = { rootNavController.popBackStack() })
        }
    }
}

@Composable
fun MainApp(rootNavController: NavHostController) {
    val mainNavController = rememberNavController()

    Scaffold(
        topBar = { MainTopAppBar(rootNavController) },
        bottomBar = { MainBottomNavBar(mainNavController) }
    ) { innerPadding ->
        NavGraphApp(
            navController = mainNavController,
            modifier = Modifier,
            padding = innerPadding
        )
    }
}