package ru.mugalimov.volthome.application

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.yandex.metrica.YandexMetrica
import com.yandex.metrica.YandexMetricaConfig
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.Secret
import javax.inject.Inject

@HiltAndroidApp
class VoltHomeApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var metricaInit = false

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()

    override fun onCreate() {
        super.onCreate()

        // AppMetrica: тяжёлую часть уводим в Default, чтобы не блокировать первый кадр UI.
        if (!metricaInit) {
            appScope.launch {
                runCatching {
                    val cfg = withContext(Dispatchers.Default) {
                        YandexMetricaConfig
                            .newConfigBuilder(Secret.APP_METRICA_API_KEY)
                            .withLogs()
                            .build()
                    }
                    // Активируем SDK в фоне
                    YandexMetrica.activate(applicationContext, cfg)
                }.onSuccess {
                    // Лёгкую часть можно вызвать на main — она не тяжёлая
                    withContext(Dispatchers.Main) {
                        YandexMetrica.enableActivityAutoTracking(this@VoltHomeApp)
                    }
                    metricaInit = true
                }.onFailure {
                    Log.w("VoltHomeApp", "AppMetrica init failed: ${it.message}", it)
                }
            }
        }
    }
}