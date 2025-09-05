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
import com.yandex.metrica.YandexMetrica
import com.yandex.metrica.YandexMetricaConfig
import dagger.hilt.android.AndroidEntryPoint
import ru.mugalimov.volthome.ui.navigation.RootNavGraph
import ru.mugalimov.volthome.ui.navigation.Screens
import ru.mugalimov.volthome.ui.screens.welcome.AppTheme


@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        @Volatile private var metricaInit = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Инициализация AppMetrica — один раз за процесс
        if (!metricaInit) {
            val config = YandexMetricaConfig
                .newConfigBuilder(Secret.APP_METRICA_API_KEY)
                .withLogs()
                .build()

            YandexMetrica.activate(applicationContext, config)
            // Передаём именно Application, как требует SDK
            YandexMetrica.enableActivityAutoTracking(application)

            metricaInit = true
        }

        // 🚫 Запрещаем ночной режим на уровне всего приложения
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

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
//        !isOnboardingShown.value -> Screens.OnBoardingScreen.route
        else -> Screens.MainApp.route
    }

    AppTheme {
        RootNavGraph(
            startDestination = startDestination,
            onFirstLaunchCompleted = {
                prefs.edit().putBoolean("first_launch", false).apply()
                isFirstLaunch.value = false
            }
        )
    }
}


