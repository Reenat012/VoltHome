package ru.mugalimov.volthome

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import com.yandex.authsdk.YandexAuthSdk
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import ru.mugalimov.volthome.core.theme.VoltHomeTheme
import ru.mugalimov.volthome.data.billing.RustoreBillingManager
import ru.mugalimov.volthome.ui.navigation.RootNavGraph
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var yandexSdk: YandexAuthSdk
    @Inject lateinit var rustoreBillingManager: RustoreBillingManager

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val flowId = newFlowId(prefix = "activity")

        logBegin(
            operation = "onCreate",
            flowId = flowId,
            extra = "hasSavedState=${savedInstanceState != null}"
        )

        super.onCreate(savedInstanceState)

        val cid = BuildConfig.YANDEX_CLIENT_ID
        Log.d("YA_AUTH", "client_id set: ${cid.isNotBlank()}, tail=***${cid.takeLast(3)}")

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        setContent {
            VoltHomeTheme {
                RootNavGraph(sdk = yandexSdk)
            }
        }

        logEnd(
            operation = "onCreate",
            flowId = flowId,
            outcome = "CONTENT_SET"
        )
    }

    override fun onNewIntent(intent: Intent) {
        val flowId = newFlowId(prefix = "deeplink")

        logBegin(
            operation = "onNewIntent",
            flowId = flowId,
            extra = "intentData=${intent.data}"
        )

        super.onNewIntent(intent)
        Log.d("YA_AUTH", "onNewIntent data=${intent.data}")

        try {
            // Это не purchaseFlowId покупки, а отдельная системная трассировка deeplink-обработки.
            rustoreBillingManager.handleDeeplinkIntent(intent = intent, flowId = flowId)

            logEnd(
                operation = "onNewIntent",
                flowId = flowId,
                outcome = "DEEPLINK_DISPATCHED"
            )
        } catch (t: Throwable) {
            logError(
                operation = "onNewIntent",
                flowId = flowId,
                outcome = "FAILED",
                throwable = t
            )
            throw t
        }
    }

    /**
     * Генерация короткого flow id для системных шагов Activity.
     */
    private fun newFlowId(prefix: String): String {
        val tail = UUID.randomUUID().toString().replace("-", "").take(12)
        return "$prefix-$tail"
    }

    /**
     * Лог начала шага.
     */
    private fun logBegin(
        operation: String,
        flowId: String,
        extra: String? = null
    ) {
        val message = buildString {
            append("BEGIN")
            append(" op=").append(operation)
            append(" flowId=").append(flowId)
            if (!extra.isNullOrBlank()) append(" ").append(extra)
        }
        Log.d(TAG, message)
    }

    /**
     * Лог завершения шага.
     */
    private fun logEnd(
        operation: String,
        flowId: String,
        outcome: String
    ) {
        val message = buildString {
            append("END")
            append(" op=").append(operation)
            append(" flowId=").append(flowId)
            append(" outcome=").append(outcome)
        }
        Log.d(TAG, message)
    }

    /**
     * Error-лог.
     */
    private fun logError(
        operation: String,
        flowId: String,
        outcome: String,
        throwable: Throwable
    ) {
        val message = buildString {
            append("END")
            append(" op=").append(operation)
            append(" flowId=").append(flowId)
            append(" outcome=").append(outcome)
            append(" errorClass=").append(throwable.javaClass.simpleName)
            append(" errorMessage=").append(throwable.message)
        }
        Log.e(TAG, message, throwable)
    }
}
