package ru.mugalimov.volthome.data.sync.outbox

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Воркер для пуша outbox. Умеет:
 *  - пушить всё (enqueueAll)
 *  - пушить по проекту (enqueueProject)
 *
 * Констрейнты:
 *  - требуется валидное сетевое соединение (CONNECTED)
 *  - экспоненциальный бэкофф
 *
 * Дополнительно:
 *  - single-flight на процесс: одновременно пушит только один воркер
 *  - небольшой debounce на старте, чтобы схлопывать всплески запуска
 */
private object OutboxSingleFlight {
    val busy = AtomicBoolean(false)
}

@HiltWorker
class OutboxPushWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pusher: OutboxPusher
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Мягкий debounce: схлопнуть пачку почти одновременных запусков
        delay(600)

        val projectId = inputData.getString(KEY_PROJECT_ID)
        val isAll = projectId.isNullOrBlank()

        // Single-flight: если уже кто-то пушит — текущую работу тихо пропускаем
        if (!OutboxSingleFlight.busy.compareAndSet(false, true)) {
            if (isAll) {
                Log.i(TAG, "another push in-flight; skip ALL")
            } else {
                Log.i(TAG, "another push in-flight; skip project=$projectId")
            }
            return@withContext Result.success()
        }

        try {
            val stats = if (isAll) {
                Log.i(TAG, "push ALL outbox")
                pusher.pushAll()
            } else {
                Log.i(TAG, "push outbox for project=$projectId")
                pusher.pushProject(projectId!!)
            }

            if (stats.failed > 0) {
                Log.w(TAG, "Outbox push completed with failed=${stats.failed}, scheduling retry…")
                Result.retry()
            } else {
                Result.success()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Outbox push failed: ${t.message}", t)
            Result.retry()
        } finally {
            OutboxSingleFlight.busy.set(false)
        }
    }

    companion object {
        private const val TAG = "OutboxPushWorker"
        private const val KEY_PROJECT_ID = "project_id"

        private const val UNIQUE_ALL = "outbox_push_all"
        private const val UNIQUE_PROJECT_PREFIX = "outbox_push_project_"

        private const val TAG_ALL = "tag_outbox_all"
        private const val TAG_PROJECT_PREFIX = "tag_outbox_project_"

        // Требуем подключение к интернету, чтобы пуш не выполнялся оффлайн
        private val NET_CONSTRAINTS: Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        private fun baseRequestBuilder(): OneTimeWorkRequest.Builder =
            OneTimeWorkRequest.Builder(OutboxPushWorker::class.java)
                .setConstraints(NET_CONSTRAINTS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                // Лёгкий начальный джиттер, чтобы Greedy/System не стартовали кучу работ в один тик
                .setInitialDelay(500, TimeUnit.MILLISECONDS)

        /** Пушить всё */
        fun enqueueAll(context: Context) {
            val req: OneTimeWorkRequest = baseRequestBuilder()
                .addTag(TAG_ALL)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ALL, ExistingWorkPolicy.KEEP, req
            )
        }

        /** Пуш по проекту (уникальна на проект).
         *  Включая draft-* — публикацию и последующий batch сделает OutboxPusher.
         */
        fun enqueueProject(context: Context, projectId: String) {
            val input: Data = Data.Builder()
                .putString(KEY_PROJECT_ID, projectId)
                .build()

            val unique = "$UNIQUE_PROJECT_PREFIX$projectId"
            val tag = "$TAG_PROJECT_PREFIX$projectId"

            val req: OneTimeWorkRequest = baseRequestBuilder()
                .setInputData(input)
                .addTag(tag)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                unique, ExistingWorkPolicy.KEEP, req
            )
        }
    }
}