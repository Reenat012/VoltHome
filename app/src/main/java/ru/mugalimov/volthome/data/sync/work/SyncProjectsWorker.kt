package ru.mugalimov.volthome.data.sync.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.room.withTransaction
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
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import ru.mugalimov.volthome.data.local.datastore.ActiveProjectDataStore
import ru.mugalimov.volthome.data.remote.api.CreateProjectRequest
import ru.mugalimov.volthome.data.remote.api.ProjectsApi
import ru.mugalimov.volthome.data.sync.SyncManager
import ru.mugalimov.volthome.di.database.AppDatabase
import ru.mugalimov.volthome.util.TimeUtils
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncProjectsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val db: AppDatabase,
    private val api: ProjectsApi,
    private val syncManager: SyncManager,
    private val activeProjectDataStore: ActiveProjectDataStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val projectId = inputData.getString(KEY_PROJECT_ID)
        try {
            if (projectId.isNullOrBlank()) {
                // Синк активного
                syncManager.syncActiveProject()
                Result.success()
            } else {
                // Если локальный драфт — публикуем, затем синкаем по remoteId
                val maybeRemote = publishDraftIfNeeded(projectId)
                val idForSync = maybeRemote ?: projectId
                syncManager.syncProject(idForSync)
                Result.success()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "sync failed: ${t.message}", t)
            Result.retry()
        }
    }

    private suspend fun publishDraftIfNeeded(localProjectId: String): String? {
        val stateDao = db.projectLocalStateDao()
        val projectDao = db.projectDao()

        val isDraftId = localProjectId.startsWith("draft-")
        val st = stateDao.get(localProjectId)
        if (!isDraftId || st == null || st.remote_version != 0) return null

        val p = projectDao.getById(localProjectId) ?: return null
        Log.i(TAG, "publish draft: localId=$localProjectId, name='${p.name}'")

        try {
            val created = api.createProject(CreateProjectRequest(name = p.name, note = p.note))
            val remoteId = created.id
            Log.i(TAG, "draft published ok: remoteId=$remoteId (v${created.version})")

            val roomDao = db.roomDao()
            val deviceDao = db.deviceDao()
            val groupDaoOrNull = runCatching { db.groupDao() }.getOrNull()

            db.withTransaction {
                // новая серверная запись
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
                // ребайнд зависимостей
                val roomsRebound = roomDao.rebindProjectRooms(localProjectId, remoteId)
                val devicesRebound = deviceDao.rebindProjectDevices(localProjectId, remoteId)
                val groupsRebound = if (groupDaoOrNull != null) {
                    runCatching { groupDaoOrNull.rebindProjectGroups(localProjectId, remoteId) }.getOrElse { 0 }
                } else 0
                Log.i(TAG, "rebind done: rooms=$roomsRebound, devices=$devicesRebound, groups=$groupsRebound")

                // Считываем старое состояние draft до удаления,
// чтобы не потерять manual lock / bootstrapVersion / marker
                val oldState = stateDao.get(localProjectId)

// Если marker ссылался на draft-id,
// переводим его на новый remoteId
                val migratedActiveManualProjectId =
                    if (oldState?.active_manual_project_id == localProjectId) {
                        created.id
                    } else {
                        oldState?.active_manual_project_id
                    }

                // Удаляем старую строку состояния draft
                stateDao.delete(localProjectId)

                // Переносим состояние на новый remoteId с сохранением ownership-полей
                val newState =
                    (oldState ?: ru.mugalimov.volthome.data.local.entity.ProjectLocalStateEntity(
                        project_id = created.id,
                        remote_version = 0,
                        last_sync_at = null,
                        has_local_changes = false
                    )).copy(
                        project_id = created.id,
                        remote_version = created.version,
                        last_sync_at = TimeUtils.formatIso(TimeUtils.now()),
                        has_local_changes = true,
                        active_manual_project_id = migratedActiveManualProjectId
                        // manual_overrides_present и manual_lock_bootstrap_version
                        // сохраняются автоматически через copy(...)
                    )

                stateDao.upsert(newState)

                Log.i(
                    TAG,
                    "PROJECT_STATE_WRITE source=SyncProjectsWorker.publishDraft " +
                            "pidOld=$localProjectId pidNew=${created.id} " +
                            "lockBefore=${oldState?.manual_overrides_present} " +
                            "lockAfter=${newState.manual_overrides_present} " +
                            "bootstrapBefore=${oldState?.manual_lock_bootstrap_version} " +
                            "bootstrapAfter=${newState.manual_lock_bootstrap_version} " +
                            "markerBefore=${oldState?.active_manual_project_id} " +
                            "markerAfter=${newState.active_manual_project_id}"
                )

                // удаляем строку драфта
                projectDao.deleteById(localProjectId)
            }

            // переключаем активный проект
            runCatching {
                Log.i(TAG, "active project switched to remoteId=$remoteId from localId=$localProjectId")
                activeProjectDataStore.setActiveProjectId(remoteId)
            }.onFailure {
                Log.w(TAG, "failed to setActiveProjectId($remoteId): ${it.message}", it)
            }

            // ⛔️ НЕ enqueue здесь — текущий воркер уже вызовет syncProject(remoteId)
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
        private const val TAG = "SyncProjectsWorker"
        private const val KEY_PROJECT_ID = "project_id"
        private const val UNIQUE_NAME_PREFIX = "sync-project-"

        // Требуем сеть и щадим батарею
        private val NET_CONSTRAINTS: Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        private fun baseRequestBuilder(): OneTimeWorkRequest.Builder =
            OneTimeWorkRequest.Builder(SyncProjectsWorker::class.java)
                .setConstraints(NET_CONSTRAINTS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInitialDelay(300, TimeUnit.MILLISECONDS)

        fun enqueue(context: Context, projectId: String) {
            val unique = "$UNIQUE_NAME_PREFIX$projectId"
            val input: Data = Data.Builder()
                .putString(KEY_PROJECT_ID, projectId)
                .build()

            val req: OneTimeWorkRequest = baseRequestBuilder()
                .setInputData(input)
                .addTag(unique)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                unique,
                ExistingWorkPolicy.KEEP,
                req
            )
        }
    }
}