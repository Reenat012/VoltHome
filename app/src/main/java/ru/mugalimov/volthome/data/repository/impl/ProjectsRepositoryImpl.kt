package ru.mugalimov.volthome.data.repository.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.data.local.datastore.ActiveProjectDataStore
import ru.mugalimov.volthome.data.local.entity.ProjectEntity
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

    // ----------- защита от параллельных/повторных запусков -----------
    private val bootstrapMutex = Mutex()
    private val isBootstrapping = AtomicBoolean(false)

    // --------------------------- LIST ---------------------------
    override fun listProjects(): Flow<List<Project>> =
        db.projectDao()
            .observeAll()
            .map { list -> list.map { it.toDomain() } }

    // --------------------------- ensureActiveDraft ---------------------------
    override suspend fun ensureActiveDraft(): String = withContext(Dispatchers.IO) {
        // 1) Если активный уже выбран и есть в БД — возвращаем его.
        val current = activeProjectDataStore.activeProjectId.firstOrNull()
        if (!current.isNullOrBlank()) {
            if (db.projectDao().getById(current) != null) return@withContext current
        }

        // 2) Пробуем выбрать самый свежий удалённый/локальный проект, если он есть
        val all = db.projectDao().observeAll().firstOrNull().orEmpty()
        val latest = all.maxByOrNull { it.updated_at }
        if (latest != null) {
            activeProjectDataStore.setActiveProjectId(latest.id)
            return@withContext latest.id
        }

        // 3) Иначе создаём ЛОКАЛЬНЫЙ ЧЕРНОВИК (без POST!)
        val id = "draft-" + UUID.randomUUID().toString()
        val nowIso = TimeUtils.formatIso(TimeUtils.now())
        db.projectDao().upsert(
            ProjectEntity(
                id = id,
                name = "Новый проект",
                note = null,
                version = 0,                 // <— ключ: 0 = «черновик/не опубликован»
                updated_at = nowIso,
                is_deleted = false
            )
        )
        // локальное состояние
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

    // --------------------------- BOOTSTRAP ---------------------------
    override suspend fun bootstrapFromRemote(): Int = withContext(Dispatchers.IO) {
        // защита от двоекратного запуска (из разных вьюмоделей при старте)
        if (!isBootstrapping.compareAndSet(false, true)) return@withContext 0
        try {
            bootstrapMutex.withLock {
                var imported = 0
                var cursor: String? = null
                val stateDao = db.projectLocalStateDao()

                // простой вариант — сервер сам отдаёт всю страницу, next всегда null
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

                // Если активный не выбран — пусть будет самый свежий (но НЕ создаём новый проект здесь!)
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

    // --------------------------- CREATE ---------------------------
    override suspend fun createProject(name: String, note: String?): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val nowIso = TimeUtils.formatIso(TimeUtils.now())

        db.projectDao().upsert(
            ProjectEntity(
                id = id,
                name = name,
                note = note,
                version = 0, // сервер потом вернёт >=1
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

        // Best-effort POST
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

    // --------------------------- RENAME ---------------------------
    override suspend fun renameProject(id: String, name: String) {
        withContext(Dispatchers.IO) {
            val nowIso = TimeUtils.formatIso(TimeUtils.now())
            val dao = db.projectDao()
            val current = dao.getById(id)

            dao.upsert(
                (current ?: ProjectEntity(
                    id = id,
                    name = name,
                    note = null,
                    version = 0,
                    updated_at = nowIso,
                    is_deleted = false
                )).copy(
                    name = name,
                    updated_at = nowIso
                )
            )

            val stateDao = db.projectLocalStateDao()
            val st = stateDao.get(id)
            stateDao.upsert(
                (st ?: ru.mugalimov.volthome.data.local.entity.ProjectLocalStateEntity(
                    project_id = id,
                    remote_version = 0,
                    last_sync_at = null,
                    has_local_changes = false
                )).copy(has_local_changes = true)
            )

            // Best-effort PUT
            runCatching {
                val updated = api.updateProjectMeta(id, UpdateProjectRequest(name = name, note = current?.note))
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
    }

    // --------------------------- DELETE ---------------------------
    override suspend fun deleteProject(id: String) {
        withContext(Dispatchers.IO) {
            val nowIso = TimeUtils.formatIso(TimeUtils.now())
            val dao = db.projectDao()
            val current = dao.getById(id) ?: return@withContext

            dao.softDelete(id = id, updatedAt = nowIso, version = current.version)

            val stateDao = db.projectLocalStateDao()
            val st = stateDao.get(id)
            stateDao.upsert(
                (st ?: ru.mugalimov.volthome.data.local.entity.ProjectLocalStateEntity(
                    project_id = id,
                    remote_version = 0,
                    last_sync_at = null,
                    has_local_changes = false
                )).copy(has_local_changes = true)
            )

            // Best-effort DELETE
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
            }
        }
    }

    // --------------------------- OPEN ---------------------------
    override suspend fun openProject(id: String) {
        // ВАЖНО: сначала зафиксировать выбор, потом синк (чтобы UI уже знал projectId)
        activeProjectDataStore.setActiveProjectId(id)
        runCatching { syncManager.syncProject(id) }
    }

    // --------------------------- MAPPERS ---------------------------
    private fun ProjectEntity.toDomain(): Project =
        Project(
            id = id,
            name = name,
            note = note,
            version = version,
            updatedAt = updated_at,
            isDeleted = is_deleted
        )
}