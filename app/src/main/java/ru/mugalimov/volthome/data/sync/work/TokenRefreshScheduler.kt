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
     * Запланировать тихий refresh за leewayMs до истечения.
     * Если время уже прошло — ставим на ближайшее будущее (delay=0).
     */
    fun scheduleFromExpiry(expiresAtMillis: Long, leewayMs: Long = 120_000L) {
        val delayMs = max(0L, (expiresAtMillis - System.currentTimeMillis()) - leewayMs)
        TokenRefreshWorker.scheduleOneTime(workManager, delayMs)
    }
}