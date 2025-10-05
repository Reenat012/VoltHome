package ru.mugalimov.volthome.data.sync.outbox

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Воркер для пуша outbox. Умеет:
 *  - пушить всё (enqueueAll)
 *  - пушить по проекту (enqueueProject)
 *
 * Констрейнты:
 *  - требуется валидное сетевое соединение (CONNECTED)
 *  - не запускаем на низком заряде
 *  - экспоненциальный бэкофф
 *
 * Уникальность:
 *  - общий пуш: UNIQUE_ALL (KEEP)
 *  - пуш по проекту: UNIQUE_PROJECT_PREFIX + projectId (KEEP)
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

        // Ключи/unique names/tags
        private const val KEY_PROJECT_ID = "project_id"
        private const val UNIQUE_ALL = "outbox_push_all"
        private const val UNIQUE_PROJECT_PREFIX = "outbox_push_project_"
        private const val TAG_ALL = "tag_outbox_all"
        private const val TAG_PROJECT_PREFIX = "tag_outbox_project_"

        // Общие констрейнты для всех запусков
        private val NET_CONSTRAINTS = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        private fun buildRequest(projectId: String? = null): OneTimeWorkRequest {
            val builder = OneTimeWorkRequestBuilder<OutboxPushWorker>()
                .setConstraints(NET_CONSTRAINTS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)

            if (!projectId.isNullOrBlank()) {
                builder.setInputData(workDataOf(KEY_PROJECT_ID to projectId))
                    .addTag(TAG_PROJECT_PREFIX + projectId)
            } else {
                builder.addTag(TAG_ALL)
            }
            return builder.build()
        }

        /** Единичный пуш «всё» (уникален, без дубликатов) */
        fun enqueueAll(context: Context) {
            val req = buildRequest()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_ALL, ExistingWorkPolicy.KEEP, req)
        }

        /** Единичный пуш по проекту (уникален на projectId) */
        fun enqueueProject(context: Context, projectId: String) {
            val unique = UNIQUE_PROJECT_PREFIX + projectId
            val req = buildRequest(projectId)
            WorkManager.getInstance(context)
                .enqueueUniqueWork(unique, ExistingWorkPolicy.KEEP, req)
        }
    }
}