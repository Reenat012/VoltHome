package ru.mugalimov.volthome.data.sync.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ru.mugalimov.volthome.data.remote.auth.RefreshCoordinator
import ru.mugalimov.volthome.data.remote.auth.SessionManager
import java.util.concurrent.TimeUnit

@HiltWorker
class TokenRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val sessionManager: SessionManager,
    private val refreshCoordinator: RefreshCoordinator
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Если ничего не надо — успешно выходим
        if (!sessionManager.needsRefresh()) return Result.success()

        val refreshed = refreshCoordinator.tryRefresh()
        return if (refreshed != null) Result.success() else Result.retry()
    }

    companion object {
        private const val UNIQUE_NAME = "token_refresh_once"

        fun scheduleOneTime(workManager: WorkManager, delayMs: Long) {
            val req = OneTimeWorkRequestBuilder<TokenRefreshWorker>()
                .setInitialDelay(delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10, TimeUnit.SECONDS
                )
                .build()

            workManager.enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.REPLACE,
                req
            )
        }
    }
}