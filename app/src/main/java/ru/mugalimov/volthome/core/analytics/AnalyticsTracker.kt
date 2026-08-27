package ru.mugalimov.volthome.core.analytics

import android.util.Log
import com.yandex.metrica.YandexMetrica
import javax.inject.Inject
import javax.inject.Singleton
import ru.mugalimov.volthome.BuildConfig

@Singleton
class AnalyticsTracker @Inject constructor() {

    @Volatile
    private var collectionEnabled: Boolean = false

    fun setCollectionEnabled(enabled: Boolean) {
        collectionEnabled = enabled
    }

    fun track(event: AnalyticsEvent) {
        val report = event.toReport()

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "event=${report.name} parameters=${report.parameters}")
            return
        }

        if (!collectionEnabled) return

        runCatching {
            YandexMetrica.reportEvent(report.name, report.parameters)
        }.onFailure { error ->
            Log.w(TAG, "Unable to report ${report.name}: ${error.message}", error)
        }
    }

    private companion object {
        const val TAG = "AnalyticsTracker"
    }
}
