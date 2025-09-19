package ru.mugalimov.volthome

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.yandex.metrica.YandexMetrica
import com.yandex.metrica.YandexMetricaConfig
import com.yandex.authsdk.YandexAuthSdk
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import ru.mugalimov.volthome.ui.navigation.RootNavGraph
import ru.mugalimov.volthome.ui.navigation.Screens
import ru.mugalimov.volthome.ui.screens.welcome.AppTheme

// CompositionLocal: из любого экрана можно вызвать авторизацию
val LocalVkidAuthorize = compositionLocalOf<(Set<String>) -> Unit> {
    { _ -> /* no-op by default */ }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        @Volatile
        private var metricaInit = false
    }

    @Inject
    lateinit var yandexSdk: YandexAuthSdk

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cid = BuildConfig.YANDEX_CLIENT_ID
        Log.d("YA_AUTH", "client_id set: ${cid.isNotBlank()}, tail=***${cid.takeLast(3)}")

        // Инициализация AppMetrica — один раз за процесс
        if (!metricaInit) {
            val config = YandexMetricaConfig
                .newConfigBuilder(Secret.APP_METRICA_API_KEY)
                .withLogs()
                .build()
            YandexMetrica.activate(applicationContext, config)
            YandexMetrica.enableActivityAutoTracking(application)
            metricaInit = true
        }

        // 🚫 Отключаем ночной режим глобально
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        setContent {
            VoltHomeApp(sdk = yandexSdk)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("YA_AUTH", "onNewIntent data=${intent?.data}")
    }
}

/**
 * Корневой компонент приложения, обёрнутый в Material-тему.
 */
@Composable
fun VoltHomeApp(
    sdk: YandexAuthSdk
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    val isFirstLaunch = remember { mutableStateOf(prefs.getBoolean("first_launch", true)) }
    val isOnboardingShown = remember { mutableStateOf(prefs.getBoolean("onboarding_shown", false)) }

    val startDestination = when {
        isFirstLaunch.value -> Screens.WelcomeScreen.route
        // !isOnboardingShown.value -> Screens.OnBoardingScreen.route
        else -> Screens.MainApp.route
    }

    AppTheme {
        RootNavGraph(
//            startDestination = startDestination,
            sdk = sdk,
//            onFirstLaunchCompleted = {
//                prefs.edit().putBoolean("first_launch", false).apply()
//                isFirstLaunch.value = false
//            }
        )
    }
}