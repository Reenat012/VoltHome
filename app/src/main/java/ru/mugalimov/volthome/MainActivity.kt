// MainActivity.kt
package ru.mugalimov.volthome

import MainAppEntryPoint
import MainScreen
import WelcomeScreen
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.AndroidEntryPoint
import ru.mugalimov.volthome.ui.navigation.RootNavGraph
import ru.mugalimov.volthome.ui.navigation.Screens
import ru.mugalimov.volthome.ui.screens.welcome.AppTheme


@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // Используем VoltHomeApp как корневой компонент
            VoltHomeApp()
        }
    }
}

/**
 * Корневой компонент приложения, обернутый в Material Design тему.
 * Определяет общую тему и стили для всего приложения.
 */
// MainActivity.kt
@Composable
fun VoltHomeApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    // Определяем состояние первого запуска и показа онбординга
    val isFirstLaunch = remember { mutableStateOf(prefs.getBoolean("first_launch", true)) }
    val isOnboardingShown = remember { mutableStateOf(prefs.getBoolean("onboarding_shown", false)) }

    // Определяем начальный экран
    val startDestination = when {
        isFirstLaunch.value -> Screens.WelcomeScreen.route
        !isOnboardingShown.value -> Screens.OnBoardingScreen.route
        else -> Screens.MainApp.route
    }

    AppTheme {
        RootNavGraph(
            startDestination = startDestination,
            onFirstLaunchCompleted = {
                prefs.edit().putBoolean("first_launch", false).apply()
                isFirstLaunch.value = false
            },
            onOnboardingCompleted = {
                prefs.edit().putBoolean("onboarding_shown", true).apply()
                isOnboardingShown.value = true
            }
        )
    }
}


