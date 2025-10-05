package ru.mugalimov.volthome.data.sync.outbox

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.BackoffPolicy
import java.util.concurrent.TimeUnit

/**
 * Воркер для пуша outbox. Умеет:
 *  - пушить всё (enqueueAll)
 *  - пушить по проекту (enqueueProject)
 */
@HiltWorker
class OutboxPushWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pusher: OutboxPusher
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val projectId = inputData.getString(KEY_PROJECT_ID)
        try {
            if (projectId.isNullOrBlank()) {
                Log.i(TAG, "push ALL outbox")
                pusher.pushAll()
            } else {
                Log.i(TAG, "push outbox for project=$projectId")
                pusher.pushProject(projectId)
            }
            Result.success()
        } catch (t: Throwable) {
            Log.w(TAG, "Outbox push failed: ${t.message}", t)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "OutboxPushWorker"
        private const val UNIQUE_ALL = "outbox-push-all"
        private const val UNIQUE_PROJECT_PREFIX = "outbox-push-project-"
        private const val KEY_PROJECT_ID = "project_id"

        private val NET_CONSTRAINTS = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // ← не запускать оффлайн
            .build()

        /** Пушить всё */
        fun enqueueAll(context: Context) {
            val req = OneTimeWorkRequestBuilder<OutboxPushWorker>()
                .setConstraints(NET_CONSTRAINTS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ALL, ExistingWorkPolicy.KEEP, req
            )
        }

        /** Пуш по проекту (уникальна на проект) */
        fun enqueueProject(context: Context, projectId: String) {
            val req = OneTimeWorkRequestBuilder<OutboxPushWorker>()
                .setInputData(workDataOf(KEY_PROJECT_ID to projectId))
                .setConstraints(NET_CONSTRAINTS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$UNIQUE_PROJECT_PREFIX$projectId", ExistingWorkPolicy.KEEP, req
            )
        }
    }
}