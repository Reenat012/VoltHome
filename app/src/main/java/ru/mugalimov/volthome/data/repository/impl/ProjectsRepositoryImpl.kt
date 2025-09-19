package ru.mugalimov.volthome.data.repository.impl

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.mugalimov.volthome.data.local.entity.ProjectEntity
import ru.mugalimov.volthome.data.remote.api.ProjectsApi
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
    private val syncManager: SyncManager
) : ProjectsRepository {

    // ---------------------------
    // LIST (кэш → просто Flow из Room)
    // ---------------------------
    override fun listProjects(): Flow<List<Project>> =
        db.projectDao()
            .observeAll()
            .map { list -> list.map { it.toDomain() } }

    // ---------------------------
    // CREATE (оффлайн-первый)
    // ---------------------------
    override suspend fun createProject(name: String, note: String?) {
        val id = UUID.randomUUID().toString()
        val nowIso = TimeUtils.formatIso(TimeUtils.now())

        // 1) Локально создаём проект, помечаем «есть локальные изменения»
        db.projectDao().upsert(
            ProjectEntity(
                id = id,
                name = name,
                note = note,
                version = 0,                 // станет 1 после успешного push
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

        // 2) Синк в фоне (Pull→Push). Если хочешь — вызывать из VM через WorkManager.
        // Здесь можно сразу инициировать прямой синк:
        // syncManager.syncProject(id)
    }

    // ---------------------------
    // RENAME (оффлайн-первый)
    // ---------------------------
    override suspend fun renameProject(id: String, name: String) {
        val nowIso = TimeUtils.formatIso(TimeUtils.now())
        val dao = db.projectDao()
        val current = dao.getById(id)

        // upsert локально с новым именем
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

        // пометить «есть локальные изменения»
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
    }

    // ---------------------------
    // DELETE (soft, оффлайн-первый)
    // ---------------------------
    override suspend fun deleteProject(id: String) {
        val nowIso = TimeUtils.formatIso(TimeUtils.now())
        val dao = db.projectDao()
        val current = dao.getById(id) ?: return

        // мягкое удаление локально (флаг tombstone)
        dao.softDelete(
            id = id,
            updatedAt = nowIso,
            version = current.version
        )

        // пометить «есть локальные изменения»
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
    }

    // ---------------------------
    // OPEN (зафиксировать выбор + синк)
    // ---------------------------
    override suspend fun openProject(id: String) {
        // Здесь мы не знаем, где у тебя хранится activeProjectId (DataStore/Preferences/БД).
        // Если он ведётся в DataStore — сохраняй его там из слоя, который вызывает репозиторий.
        // В рамках текущей зависимости (db + api + syncManager) инициируем синхронизацию проекта:
        syncManager.syncProject(id)
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