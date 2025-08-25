// MainActivity.kt
package ru.mugalimov.volthome

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import ru.mugalimov.volthome.ui.navigation.RootNavGraph
import ru.mugalimov.volthome.ui.navigation.Screens
import ru.mugalimov.volthome.ui.screens.welcome.AppTheme
import ru.mugalimov.volthome.ui.viewmodel.AppSyncViewModel


@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🚫 Запрещаем ночной режим на уровне всего приложения
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        setContent {
            // просто инициализируем, init{} внутри запустит авто‑пересчёт
//            val _appSyncVm: AppSyncViewModel = hiltViewModel()

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


