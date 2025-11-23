package ru.mugalimov.volthome.data.sync.work

import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class TokenRefreshScheduler @Inject constructor(
    private val workManager: WorkManager
) {
    /**
     * Единое планирование обновления токена.
     *
     * По умолчанию запускаем воркер за ~5 минут до истечения access-токена.
     * Без expedited, только обычный OneTimeWorkRequest с backoff.
     */
    fun schedule(
        expiresAtMillis: Long,
        leewayMs: Long = TimeUnit.MINUTES.toMillis(5) // было 8 минут, теперь 5
    ) {
        val now = System.currentTimeMillis()
        val delayMs = max(0L, (expiresAtMillis - now) - leewayMs)
        TokenRefreshWorker.scheduleUnique(workManager, delayMs)
    }
}