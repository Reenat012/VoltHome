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
import ru.mugalimov.volthome.BuildConfig
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

        // 1) StrictMode — только в debug
        if (BuildConfig.DEBUG) {
            DebugStrictMode.enable()
        }

        // 2) AppMetrica: тяжёлую инициализацию уводим в Dispatchers.Default
        if (!metricaInit) {
            appScope.launch {
                runCatching {
                    val cfg = withContext(Dispatchers.Default) {
                        YandexMetricaConfig
                            .newConfigBuilder(Secret.APP_METRICA_API_KEY)
                            .withLogs()
                            .build()
                    }
                    // Активация SDK — допускается из бэкграунда
                    YandexMetrica.activate(applicationContext, cfg)
                }.onSuccess {
                    // Лёгкая часть — на main
                    YandexMetrica.enableActivityAutoTracking(this@VoltHomeApp)
                    metricaInit = true
                }.onFailure {
                    Log.w("VoltHomeApp", "AppMetrica init failed: ${it.message}", it)
                }
            }
        }
    }
}