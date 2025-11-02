package ru.mugalimov.volthome.data.sync.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ru.mugalimov.volthome.data.remote.auth.RefreshGate
import ru.mugalimov.volthome.data.remote.auth.SessionManager
import java.util.concurrent.TimeUnit

@HiltWorker
class TokenRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val sessionManager: SessionManager,
    private val refreshGate: RefreshGate
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Минимальный TTL, при котором считаем нужным обновиться: 5 минут
        val minTtlSec = 5 * 60
        val res = refreshGate.refreshIfNeeded(minTtlSec)
        return when (res) {
            is RefreshGate.Result.Succeeded -> Result.success()
            is RefreshGate.Result.Idle -> Result.success()
            is RefreshGate.Result.Failed -> {
                // Сеть/unknown — пробуем еще (экспоненциальный backoff)
                if (res.kind == RefreshGate.FailureKind.UNAUTHORIZED) Result.failure()
                else Result.retry()
            }
        }
    }

    companion object {
        private const val UNIQUE_PRIMARY = "token_refresh_primary"
        private const val UNIQUE_SECONDARY = "token_refresh_secondary"

        fun schedulePrimary(workManager: WorkManager, delayMs: Long) {
            val req = OneTimeWorkRequestBuilder<TokenRefreshWorker>()
                .setInitialDelay(delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()

            workManager.enqueueUniqueWork(
                UNIQUE_PRIMARY,
                ExistingWorkPolicy.REPLACE,
                req
            )
        }

        fun scheduleSecondary(workManager: WorkManager, delayMs: Long) {
            val builder = OneTimeWorkRequestBuilder<TokenRefreshWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)

            if (delayMs <= 0L) {
                // Без задержки — можно expedited
                builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            } else {
                // С задержкой expedited запрещен
                builder.setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            }

            workManager.enqueueUniqueWork(
                UNIQUE_SECONDARY,
                ExistingWorkPolicy.REPLACE,
                builder.build()
            )
        }
    }
}