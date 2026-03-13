package ru.mugalimov.volthome

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.yandex.authsdk.YandexAuthSdk
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.core.theme.VoltHomeTheme
import ru.mugalimov.volthome.data.billing.RustoreBillingManager
import ru.mugalimov.volthome.data.remote.auth.SessionManager
import ru.mugalimov.volthome.data.sync.work.TokenRefreshScheduler
import ru.mugalimov.volthome.ui.navigation.RootNavGraph
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var yandexSdk: YandexAuthSdk
    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var tokenRefreshScheduler: TokenRefreshScheduler
    @Inject lateinit var rustoreBillingManager: RustoreBillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val cid = BuildConfig.YANDEX_CLIENT_ID
        Log.d("YA_AUTH", "client_id set: ${cid.isNotBlank()}, tail=***${cid.takeLast(3)}")

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        lifecycleScope.launch(Dispatchers.Default) {
            sessionManager.load()?.let { s ->
                tokenRefreshScheduler.schedule(s.expiresAtMillis)
            }
        }

        setContent {
            VoltHomeTheme {
                var showApp by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    awaitFrame()
                    showApp = true
                }

                if (!showApp) {
                    FirstFramePlaceholder()
                } else {
                    RootNavGraph(sdk = yandexSdk)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("YA_AUTH", "onNewIntent data=${intent.data}")

        // Пробрасываем интент в RuStore Pay SDK через менеджер
        rustoreBillingManager.handleDeeplinkIntent(intent)
    }
}

@Composable
private fun FirstFramePlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "ВольтХом",
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))
            CircularProgressIndicator(strokeWidth = 2.dp)
        }
    }
}