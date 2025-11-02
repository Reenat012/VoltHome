package ru.mugalimov.volthome.data.sync.work

import androidx.work.WorkManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class TokenRefreshScheduler @Inject constructor(
    private val workManager: WorkManager
) {
    /**
     * Двухступенчатое планирование:
     *  - primary за 10 минут
     *  - secondary за 2 минуты (expedited)
     */
    fun scheduleDual(expiresAtMillis: Long) {
        val now = System.currentTimeMillis()
        val primaryDelay = (expiresAtMillis - now) - (10 * 60 * 1000L)
        val secondaryDelay = (expiresAtMillis - now) - (2 * 60 * 1000L)

        TokenRefreshWorker.schedulePrimary(workManager, primaryDelay.coerceAtLeast(0L))
        TokenRefreshWorker.scheduleSecondary(workManager, secondaryDelay) // логика внутри самa решит expedited/обычный
    }
}