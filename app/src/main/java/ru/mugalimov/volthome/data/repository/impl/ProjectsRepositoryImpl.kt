package ru.mugalimov.volthome.data.repository.impl

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import ru.mugalimov.volthome.data.local.datastore.ActiveProjectDataStore
import ru.mugalimov.volthome.data.local.entity.ProjectEntity
import ru.mugalimov.volthome.data.mapper.toDomainProject
import ru.mugalimov.volthome.data.remote.api.CreateProjectRequest
import ru.mugalimov.volthome.data.remote.api.ProjectsApi
import ru.mugalimov.volthome.data.remote.api.ProjectsApi.UpdateProjectRequest
import ru.mugalimov.volthome.data.repository.ProjectsRepository
import ru.mugalimov.volthome.data.sync.SyncManager
import ru.mugalimov.volthome.di.database.AppDatabase
import ru.mugalimov.volthome.domain.model.Project
import ru.mugalimov.volthome.util.TimeUtils
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectsRepositoryImpl @Inject constructor(
    private val api: ProjectsApi,
    private val db: AppDatabase,
    private val syncManager: SyncManager,
    private val activeProjectDataStore: ActiveProjectDataStore
) : ProjectsRepository {

    private val bootstrapMutex = Mutex()
    private val isBootstrapping = AtomicBoolean(false)

    override fun listProjects(): Flow<List<Project>> =
        db.projectDao()
            .observeAll()
            .map { list -> list.map { it.toDomainProject() } }

    override suspend fun ensureActiveDraft(): String = withContext(Dispatchers.IO) {
        val current = activeProjectDataStore.activeProjectId.firstOrNull()
        if (!current.isNullOrBlank()) {
            if (db.projectDao().getById(current) != null) return@withContext current
        }

        val all = db.projectDao().observeAll().firstOrNull().orEmpty()
        val latest = all.maxByOrNull { it.updated_at }
        if (latest != null) {
            activeProjectDataStore.setActiveProjectId(latest.id)
            return@withContext latest.id
        }

        val id = "draft-" + UUID.randomUUID().toString()
        val nowIso = TimeUtils.formatIso(TimeUtils.now())
        db.projectDao().upsert(
            ProjectEntity(
                id = id,
                name = "Новый проект",
                note = null,
                version = 0,
                updated_at = nowIso,
                is_deleted = false
            )
        )
        val stateDao = db.projectLocalStateDao()
        stateDao.upsert(
            ru.mugalimov.volthome.data.local.entity.ProjectLocalStateEntity(
                project_id = id,
                remote_version = 0,
                last_sync_at = null,
                has_local_changes = false
            )
        )
        activeProjectDataStore.setActiveProjectId(id)
        id
    }

    override suspend fun bootstrapFromRemote(): Int = withContext(Dispatchers.IO) {
        if (!isBootstrapping.compareAndSet(false, true)) return@withContext 0
        try {
            bootstrapMutex.withLock {
                var imported = 0
                var cursor: String? = null
                val stateDao = db.projectLocalStateDao()

                do {
                    val page = api.listProjects(since = null, limit = 100)
                    for (p in page.items) {
                        db.projectDao().upsert(
                            ProjectEntity(
                                id = p.id,
                                name = p.name,
                                note = p.note,
                                version = p.version,
                                updated_at = p.updated_at,
                                is_deleted = p.is_deleted
                            )
                        )
                        stateDao.upsert(
                            ru.mugalimov.volthome.data.local.entity.ProjectLocalStateEntity(
                                project_id = p.id,
                                remote_version = p.version,
                                last_sync_at = null,
                                has_local_changes = false
                            )
                        )
                        imported++
                    }
                    cursor = page.next
                } while (!cursor.isNullOrBlank())

                val activeId = activeProjectDataStore.activeProjectId.firstOrNull()
                if (activeId.isNullOrBlank()) {
                    val latest = db.projectDao().observeAll().firstOrNull()?.maxByOrNull { it.updated_at }
                    if (latest != null) activeProjectDataStore.setActiveProjectId(latest.id)
                }
                imported
            }
        } finally {
            isBootstrapping.set(false)
        }
    }

    override suspend fun createProject(name: String, note: String?): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val nowIso = TimeUtils.formatIso(TimeUtils.now())

        db.projectDao().upsert(
            ProjectEntity(
                id = id,
                name = name,
                note = note,
                version = 0,
                updated_at = nowIso,
                is_deleted = false
            )
        )
        val stateDao = db.projectLocalStateDao()
        stateDao.upsert(
            ru.mugalimov.volthome.data.local.entity.ProjectLocalStateEntity(
                project_id = id,
                remote_version = 0,
                last_sync_at = null,
                has_local_changes = true
            )
        )

        runCatching {
            val created = api.createProject(CreateProjectRequest(id = id, name = name, note = note))
            db.projectDao().upsert(
                ProjectEntity(
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
                    has_local_changes = false
                )
            )
        }
        id
    }

    override suspend fun renameProject(id: String, name: String) {
        withContext(Dispatchers.IO) {
            val nowIso = TimeUtils.formatIso(TimeUtils.now())
            val dao = db.projectDao()
            val current = dao.getById(id)

            // 1) Optimistic update локально
            dao.upsert(
                (current ?: ProjectEntity(
                    id = id, name = name, note = null, version = 0, updated_at = nowIso, is_deleted = false
                )).copy(name = name, updated_at = nowIso)
            )

            // 2) Помечаем как «грязный»
            val stateDao = db.projectLocalStateDao()
            val st = stateDao.get(id)
            stateDao.upsert(
                (st ?: ru.mugalimov.volthome.data.local.entity.ProjectLocalStateEntity(
                    project_id = id, remote_version = 0, last_sync_at = null, has_local_changes = false
                )).copy(has_local_changes = true)
            )

            // 3) Пытаемся отправить на сервер, НО безопасно
            val body = UpdateProjectRequest(name = name, note = current?.note)

            val updated = runCatching {
                api.updateProjectMetaPatch(id, body)
            }.recoverCatching { e1 ->
                if (e1 is HttpException && (e1.code() == 404 || e1.code() == 405)) {
                    api.updateProjectMetaPutLegacy(id, body)
                } else throw e1
            }.recoverCatching { e2 ->
                if (e2 is HttpException && (e2.code() == 404 || e2.code() == 405)) {
                    api.updateProjectPutRootLegacy(id, body)
                } else throw e2
            }.getOrNull()

            if (updated == null) {
                // офлайн/сервер недоступен — оставляем локальные изменения и выходим
                // (опционально) дернуть воркер синка, чтобы он сам допушил позже
                // SyncProjectsWorker.enqueue(appContext, id)  // см. пункт 3 ниже
                return@withContext
            }

            // 4) Сервер подтвердил — фиксируем и снимаем флаг «грязный»
            db.projectDao().upsert(
                ProjectEntity(
                    id = updated.id,
                    name = updated.name,
                    note = updated.note,
                    version = updated.version,
                    updated_at = updated.updated_at,
                    is_deleted = updated.is_deleted
                )
            )
            stateDao.upsert(
                ru.mugalimov.volthome.data.local.entity.ProjectLocalStateEntity(
                    project_id = updated.id,
                    remote_version = updated.version,
                    last_sync_at = TimeUtils.formatIso(TimeUtils.now()),
                    has_local_changes = false
                )
            )
        }
    }

    override suspend fun deleteProject(id: String) {
        withContext(Dispatchers.IO) {
            val nowIso = TimeUtils.formatIso(TimeUtils.now())
            val dao = db.projectDao()
            val current = dao.getById(id) ?: return@withContext

            // локальное мягкое удаление (чтобы UI сразу скрыл)
            dao.softDelete(id = id, updatedAt = nowIso, version = current.version)

            val stateDao = db.projectLocalStateDao()
            val st = stateDao.get(id)
            stateDao.upsert(
                (st ?: ru.mugalimov.volthome.data.local.entity.ProjectLocalStateEntity(
                    project_id = id, remote_version = 0, last_sync_at = null, has_local_changes = false
                )).copy(has_local_changes = true)
            )

            // Серверное удаление
            runCatching {
                val deleted = api.deleteProject(id)

                db.projectDao().upsert(
                    ProjectEntity(
                        id = deleted.id,
                        name = deleted.name,
                        note = deleted.note,
                        version = deleted.version,
                        updated_at = deleted.updated_at,
                        is_deleted = deleted.is_deleted
                    )
                )
                stateDao.upsert(
                    ru.mugalimov.volthome.data.local.entity.ProjectLocalStateEntity(
                        project_id = deleted.id,
                        remote_version = deleted.version,
                        last_sync_at = TimeUtils.formatIso(TimeUtils.now()),
                        has_local_changes = false
                    )
                )

                // 🔻 после успешного ответа сервера — подчистка локальных сущностей проекта
                db.withTransaction {
                    // Если у тебя есть соответствующие методы — оставь. Если нет — убери.
                    runCatching { db.groupDao().deleteGroupsByProject(id) }.getOrElse { /* ok */ }
                    db.deviceDao().deleteDevicesByProject(id)
                    db.roomDao().deleteRoomsByProject(id)
                }
            }

            // если удаляем активный — ProjectsViewModel уже переключит на следующий/черновик
            val active = activeProjectDataStore.activeProjectId.firstOrNull()
            if (active == id) {
                activeProjectDataStore.setActiveProjectId(null)
            }
        }
    }

    override suspend fun openProject(id: String) {
        activeProjectDataStore.setActiveProjectId(id)
        runCatching { syncManager.syncProject(id) }
    }
}