package ru.mugalimov.volthome.data.sync.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
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
        return when (val res = refreshGate.refreshIfNeeded(minTtlSec)) {
            is RefreshGate.Result.Succeeded -> Result.success()
            is RefreshGate.Result.Idle      -> Result.success()
            is RefreshGate.Result.Failed    -> {
                // UNAUTHORIZED — не ретраим; сеть/unknown — пробуем ещё с экспоненциальным бэкофом
                if (res.kind == RefreshGate.FailureKind.UNAUTHORIZED) Result.failure() else Result.retry()
            }
        }
    }

    companion object {
        private const val UNIQUE = "token_refresh_unique"

        /**
         * Единое уникальное планирование без expedited.
         * Если будет запланировано повторно — REPLACE заменит предыдущий work.
         */
        fun scheduleUnique(workManager: WorkManager, delayMs: Long) {
            val req = OneTimeWorkRequestBuilder<TokenRefreshWorker>()
                .setInitialDelay(delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()

            workManager.enqueueUniqueWork(
                UNIQUE,
                ExistingWorkPolicy.REPLACE,
                req
            )
        }
    }
}