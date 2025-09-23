package ru.mugalimov.volthome.data.repository.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectsRepositoryImpl @Inject constructor(
    private val api: ProjectsApi,
    private val db: AppDatabase,
    private val syncManager: SyncManager,
    private val activeProjectDataStore: ActiveProjectDataStore
) : ProjectsRepository {

    // ---------------------------
    // LIST (кэш → Flow из Room)
    // ---------------------------
    override fun listProjects(): Flow<List<Project>> =
        db.projectDao()
            .observeAll()
            .map { list -> list.map { it.toDomain() } }

    // ---------------------------
    // BOOTSTRAP (после чистой установки / первого логина)
    // Тянем все проекты пользователя и кладём в Room.
    // Возвращаем количество импортированных записей.
    // ---------------------------
    override suspend fun bootstrapFromRemote(): Int = withContext(Dispatchers.IO) {
        var imported = 0
        var cursor: String? = null
        val stateDao = db.projectLocalStateDao()

        do {
            val page = api.listProjects(since = null, limit = 100) // сервер сам вернёт next при необходимости
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
        } while (!cursor.isNullOrBlank()) // если у тебя реальный cursor-параметр — подставь его в listProjects

        // Если активный проект ещё не выбран — выберем самый свежий по updated_at
        val activeId = activeProjectDataStore.activeProjectId.firstOrNull()
        if (activeId.isNullOrBlank()) {
            val latest = db.projectDao().observeAll().firstOrNull()?.maxByOrNull { it.updated_at }
            latest?.let { activeProjectDataStore.setActiveProjectId(it.id) }
        }

        // Опционально: синканём активный, чтобы догрузить дерево
        activeProjectDataStore.activeProjectId.firstOrNull()?.let { id ->
            runCatching { syncManager.syncProject(id) }
        }

        imported
    }

    // ---------------------------
    // CREATE (оффлайн-первичный + best-effort онлайн)
    // ---------------------------
    override suspend fun createProject(name: String, note: String?): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val nowIso = TimeUtils.formatIso(TimeUtils.now())

        // 1) Локальный черновик
        db.projectDao().upsert(
            ProjectEntity(
                id = id,
                name = name,
                note = note,
                version = 0, // сервер после POST вернёт >=1
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

        // 2) Пробуем POST /v1/projects с нашим id
        try {
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
        } catch (_: Exception) {
            // оффлайн/ошибка — подтолкнёт воркер синка
        }

        id
    }

    // ---------------------------
    // RENAME (оффлайн-первый + best-effort PUT)
    // ---------------------------
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

            try {
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
            } catch (_: Exception) {
                // оффлайн/ошибка — подтянем при следующем синке
            }
        }
    }

    // ---------------------------
    // DELETE (soft, оффлайн-первый + best-effort DELETE)
    // ---------------------------
    override suspend fun deleteProject(id: String) {
        withContext(Dispatchers.IO) {
            val nowIso = TimeUtils.formatIso(TimeUtils.now())
            val dao = db.projectDao()
            val current = dao.getById(id) ?: return@withContext

            // локальный tombstone
            dao.softDelete(
                id = id,
                updatedAt = nowIso,
                version = current.version
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

            try {
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
            } catch (_: Exception) {
                // оффлайн/ошибка — подтянем при следующем синке
            }
        }
    }

    // ---------------------------
    // OPEN (зафиксировать выбор + синк конкретного проекта)
    // ---------------------------
    override suspend fun openProject(id: String) {
        try {
            syncManager.syncProject(id)
        } catch (_: Exception) {
            // не падаем на UI; синк повторит воркер
        }
    }

    // ---------------------------
    // MAPPERS
    // ---------------------------
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