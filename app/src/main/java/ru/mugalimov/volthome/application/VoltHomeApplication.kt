package ru.mugalimov.volthome.application

import android.app.Application
import android.util.Log
import com.yandex.metrica.YandexMetrica
import com.yandex.metrica.YandexMetricaConfig
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.BuildConfig
import ru.mugalimov.volthome.core.analytics.AnalyticsTracker
import ru.mugalimov.volthome.data.local.datastore.AnalyticsChoice
import ru.mugalimov.volthome.data.local.datastore.AppPreferences
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

@HiltAndroidApp
class VoltHomeApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var metricaInit = false

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var analyticsTracker: AnalyticsTracker

    override fun onCreate() {
        super.onCreate()

        // 1) StrictMode — только в debug
        if (BuildConfig.DEBUG) {
            DebugStrictMode.enable()
        }

        // AppMetrica не создаётся до явно сохранённого выбора пользователя.
        // UNKNOWN и DISABLED означают отсутствие отправки данных.
        if (!BuildConfig.DEBUG) observeAnalyticsChoice()
    }

    private fun observeAnalyticsChoice() {
        appScope.launch {
            appPreferences.analyticsChoice.distinctUntilChanged().collect { choice ->
                when (choice) {
                    AnalyticsChoice.ENABLED -> enableAnalytics()
                    AnalyticsChoice.DISABLED,
                    AnalyticsChoice.UNKNOWN -> {
                        analyticsTracker.setCollectionEnabled(false)
                        if (metricaInit) {
                            runCatching {
                                YandexMetrica.setStatisticsSending(applicationContext, false)
                            }.onFailure {
                                Log.w("VoltHomeApp", "Unable to disable AppMetrica: ${it.message}")
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun enableAnalytics() {
        if (BuildConfig.APP_METRICA_API_KEY.isBlank()) {
            analyticsTracker.setCollectionEnabled(false)
            Log.w("VoltHomeApp", "AppMetrica API key is not configured")
            return
        }
        if (metricaInit) {
            YandexMetrica.setStatisticsSending(applicationContext, true)
            analyticsTracker.setCollectionEnabled(true)
            return
        }
        runCatching {
            val cfg = withContext(Dispatchers.Default) {
                YandexMetricaConfig
                    .newConfigBuilder(BuildConfig.APP_METRICA_API_KEY)
                    .build()
            }
            YandexMetrica.activate(applicationContext, cfg)
        }.onSuccess {
            withContext(Dispatchers.Main.immediate) {
                YandexMetrica.enableActivityAutoTracking(this@VoltHomeApp)
                metricaInit = true
                analyticsTracker.setCollectionEnabled(true)
            }
        }.onFailure {
            Log.w("VoltHomeApp", "AppMetrica init failed: ${it.message}", it)
        }
    }
}
