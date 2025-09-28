package ru.mugalimov.volthome.data.sync.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.CoroutineWorker
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.data.remote.api.CreateProjectRequest
import ru.mugalimov.volthome.data.remote.api.ProjectsApi
import ru.mugalimov.volthome.data.sync.SyncManager
import ru.mugalimov.volthome.di.database.AppDatabase
import ru.mugalimov.volthome.util.TimeUtils

@HiltWorker
class SyncProjectsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val db: AppDatabase,
    private val api: ProjectsApi,
    private val syncManager: SyncManager
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val projectId = inputData.getString(KEY_PROJECT_ID)
        try {
            if (projectId.isNullOrBlank()) {
                // Синк активного (как было)
                syncManager.syncActiveProject()
                return@withContext Result.success()
            } else {
                // Перед синком — опубликуем локальный черновик при необходимости
                publishDraftIfNeeded(projectId)
                syncManager.syncProject(projectId)
                return@withContext Result.success()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "sync failed: ${t.message}", t)
            Result.retry()
        }
    }

    /**
     * Если проект локальный черновик (remote_version == 0) — пробуем создать его на сервере.
     * Если уже существует — ок; если сети нет — синк отложится.
     */
    private suspend fun publishDraftIfNeeded(projectId: String) {
        val stateDao = db.projectLocalStateDao()
        val st = stateDao.get(projectId) ?: return
        if (st.remote_version != 0) return // уже опубликован

        val p = db.projectDao().getById(projectId) ?: return
        Log.i(TAG, "publish draft: id=$projectId, name='${p.name}'")

        runCatching {
            val created = api.createProject(CreateProjectRequest(id = p.id, name = p.name, note = p.note))
            // Обновляем локальную запись версиями сервера
            db.projectDao().upsert(
                ru.mugalimov.volthome.data.local.entity.ProjectEntity(
                    id = created.id,
                    name = created.name,
                    note = created.note,
                    version = created.version,
                    updated_at = created.updated_at,
                    is_deleted = created.is_deleted
                )
            )
            stateDao.upsert(
                ru.mugalimov.volthome.data.local.entity.ProjectLocalStateEntity(
                    project_id = created.id,
                    remote_version = created.version,
                    last_sync_at = TimeUtils.formatIso(TimeUtils.now()),
                    has_local_changes = true // после публикации есть что синкать (комнаты/девайсы)
                )
            )
            Log.i(TAG, "draft published ok: id=$projectId (v${created.version})")
        }.onFailure {
            // Если 409/конфликт — считаем, что проект уже существует, пытаться ещё раз не мешает при следующем синке
            Log.w(TAG, "draft publish failed: ${it.message}")
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
        private const val KEY_PROJECT_ID = "project_id"
        private const val UNIQUE_PREFIX = "sync_project_"
        private const val UNIQUE_NAME_PREFIX = "sync-project-"

        fun enqueue(context: Context, projectId: String) {
            val req = OneTimeWorkRequestBuilder<SyncProjectsWorker>()
                .setInputData(workDataOf("project_id" to projectId))
                .setInitialDelay(300, java.util.concurrent.TimeUnit.MILLISECONDS)
                .addTag("$UNIQUE_NAME_PREFIX$projectId")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "$UNIQUE_NAME_PREFIX$projectId",
                ExistingWorkPolicy.KEEP,  // <— если уже в очереди, второй не ставим
                req
            )
        }
    }
}