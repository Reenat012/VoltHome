package ru.mugalimov.volthome.data.sync.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.room.withTransaction
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
import retrofit2.HttpException
import ru.mugalimov.volthome.data.local.datastore.ActiveProjectDataStore
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
    private val syncManager: SyncManager,
    private val activeProjectDataStore: ActiveProjectDataStore, // ← добавили
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
                val maybeRemote = publishDraftIfNeeded(projectId)
                val idForSync = maybeRemote ?: projectId
                syncManager.syncProject(idForSync)
                return@withContext Result.success()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "sync failed: ${t.message}", t)
            // Любая ошибка синка — попробуем позже (с бэкоффом WorkManager)
            Result.retry()
        }
    }

    /**
     * Если проект — локальный драфт (remote_version == 0), создаём его на сервере
     * БЕЗ передачи client-side id, получаем server id и ПЕРЕПРИВЯЗЫВАЕМ все FK.
     * Возвращаем remoteId, если публикация состоялась; иначе null.
     */
    private suspend fun publishDraftIfNeeded(localProjectId: String): String? {
        val stateDao = db.projectLocalStateDao()
        val projectDao = db.projectDao()

        // Подстраховка: если id не draft-* и/или remote_version != 0 — выходим
        val isDraftId = localProjectId.startsWith("draft-")
        val st = stateDao.get(localProjectId)
        if (!isDraftId || st == null || st.remote_version != 0) return null

        val p = projectDao.getById(localProjectId) ?: return null
        Log.i(TAG, "publish draft: localId=$localProjectId, name='${p.name}'")

        try {
            // НЕ передаём id — сервер сам сгенерирует UUID
            val created = api.createProject(CreateProjectRequest(name = p.name, note = p.note))
            val remoteId = created.id
            Log.i(TAG, "draft published ok: remoteId=$remoteId (v${created.version})")

            val roomDao = db.roomDao()
            val deviceDao = db.deviceDao()
            val groupDaoOrNull = runCatching { db.groupDao() }.getOrNull()

            db.withTransaction {
                // 1) Вставляем запись с remoteId
                projectDao.upsert(
                    ru.mugalimov.volthome.data.local.entity.ProjectEntity(
                        id = created.id,
                        name = created.name,
                        note = created.note,
                        version = created.version,
                        updated_at = created.updated_at,
                        is_deleted = created.is_deleted
                    )
                )

                // 2) Перепривязываем зависимые строки на remoteId
                val roomsRebound = roomDao.rebindProjectRooms(localProjectId, remoteId)
                val devicesRebound = deviceDao.rebindProjectDevices(localProjectId, remoteId)
                val groupsRebound = if (groupDaoOrNull != null) {
                    runCatching { groupDaoOrNull.rebindProjectGroups(localProjectId, remoteId) }.getOrElse { 0 }
                } else 0
                Log.i(TAG, "rebind done: rooms=$roomsRebound, devices=$devicesRebound, groups=$groupsRebound")

                // 3) Перенос local state
                stateDao.delete(localProjectId)
                stateDao.upsert(
                    ru.mugalimov.volthome.data.local.entity.ProjectLocalStateEntity(
                        project_id = created.id,
                        remote_version = created.version,
                        last_sync_at = TimeUtils.formatIso(TimeUtils.now()),
                        has_local_changes = true
                    )
                )

                // 4) Удаляем строку драфта
                projectDao.deleteById(localProjectId)
            }

            // 5) Переключаем активный проект на remoteId (безопасно) + лог
            runCatching {
                Log.i(TAG, "active project switched to remoteId=$remoteId from localId=$localProjectId")
                activeProjectDataStore.setActiveProjectId(remoteId)
            }.onFailure {
                Log.w(TAG, "failed to setActiveProjectId($remoteId): ${it.message}", it)
            }

            // 6) На всякий случай поставим новый sync сразу на remoteId (чтобы не ждать внешних триггеров)
            enqueue(applicationContext, remoteId)

            return remoteId
        } catch (e: HttpException) {
            if (e.code() == 409) {
                Log.w(TAG, "draft publish: 409 already exists; will resync later")
                return null
            }
            Log.w(TAG, "draft publish failed with HTTP ${e.code()}: ${e.message()}")
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "draft publish failed: ${t.message}", t)
            throw t
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
        private const val KEY_PROJECT_ID = "project_id"
        private const val UNIQUE_NAME_PREFIX = "sync-project-"

        fun enqueue(context: Context, projectId: String) {
            val req = OneTimeWorkRequestBuilder<SyncProjectsWorker>()
                .setInputData(workDataOf(KEY_PROJECT_ID to projectId))
                .setInitialDelay(300, java.util.concurrent.TimeUnit.MILLISECONDS)
                .addTag("$UNIQUE_NAME_PREFIX$projectId")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "$UNIQUE_NAME_PREFIX$projectId",
                ExistingWorkPolicy.KEEP,  // уже есть — второй не ставим
                req
            )
        }
    }
}