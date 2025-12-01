package ru.mugalimov.volthome

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.yandex.authsdk.YandexAuthSdk
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.remote.auth.SessionManager
import ru.mugalimov.volthome.data.sync.work.TokenRefreshScheduler
import ru.mugalimov.volthome.ui.navigation.RootNavGraph
import ru.mugalimov.volthome.ui.screens.welcome.AppTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var yandexSdk: YandexAuthSdk
    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var tokenRefreshScheduler: TokenRefreshScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val cid = BuildConfig.YANDEX_CLIENT_ID
        Log.d("YA_AUTH", "client_id set: ${cid.isNotBlank()}, tail=***${cid.takeLast(3)}")

        // Фиксируем светлую тему, чтобы исключить дорогие пересчёты на старте
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // Подстраховочно планируем refresh токена (в фоне)
        lifecycleScope.launch(Dispatchers.Default) {
            sessionManager.load()?.let { s ->
                // Используй тот метод, который есть в твоём TokenRefreshScheduler:
                // schedule(...) или scheduleDual(...). Если у тебя только scheduleDual — замени вызов.
                tokenRefreshScheduler.schedule(s.expiresAtMillis)
            }
        }

        setContent {
            AppTheme {
                var showApp by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    // Дожидаемся первого кадра, затем рендерим основную иерархию
                    awaitFrame()
                    showApp = true
                    // Здесь раньше вызывали отложенный «репортёр» — он не нужен без второго ключа.
                }

                if (!showApp) {
                    FirstFramePlaceholder()
                } else {
                    RootNavGraph(sdk = yandexSdk)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        Log.d("YA_AUTH", "onNewIntent data=${intent.data}")

        // TODO: сюда нужно будет прокинуть интент в RuStore Pay SDK
        // согласно гайду по deep links/payments, чтобы SDK завершил покупку.
        // Примерно: payClient.proceedDeeplinkIntent(intent) — см. официальную документацию.
    }
}

@Composable
private fun FirstFramePlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F6F6)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "VoltHome",
                fontSize = 22.sp,
                color = Color(0xFF1F1F1F)
            )
            Spacer(modifier = Modifier.height(12.dp))
            CircularProgressIndicator(strokeWidth = 2.dp)
        }
    }
}