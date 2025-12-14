package ru.mugalimov.volthome.data.repository.impl

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.data.local.datastore.ActiveProjectDataStore
import ru.mugalimov.volthome.data.local.entity.ProjectEntity
import ru.mugalimov.volthome.data.mapper.toDomainProject
import ru.mugalimov.volthome.data.remote.api.CreateProjectRequest
import ru.mugalimov.volthome.data.remote.api.ProjectsApi
import ru.mugalimov.volthome.data.repository.ProjectsRepository
import ru.mugalimov.volthome.data.sync.SyncManager
import ru.mugalimov.volthome.di.database.AppDatabase
import ru.mugalimov.volthome.domain.model.Project
import ru.mugalimov.volthome.util.TimeUtils
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

// outbox / tombstones
import ru.mugalimov.volthome.data.local.dao.OutboxDao
import ru.mugalimov.volthome.data.local.dao.TombstoneDao
import ru.mugalimov.volthome.data.local.entity.OutboxEntity
import ru.mugalimov.volthome.data.local.entity.OutboxOpType
import ru.mugalimov.volthome.data.local.entity.TombstoneEntity
import ru.mugalimov.volthome.data.local.entity.TombstoneEntityType
import ru.mugalimov.volthome.data.sync.outbox.OutboxPushWorker
import ru.mugalimov.volthome.data.sync.outbox.ProjectCreatePayload
import ru.mugalimov.volthome.data.sync.outbox.ProjectUpdatePayload
import ru.mugalimov.volthome.data.sync.outbox.ProjectDeletePayload
import ru.mugalimov.volthome.data.sync.outbox.toJson
import ru.mugalimov.volthome.data.sync.outbox.OutboxPusher

@Singleton
class ProjectsRepositoryImpl @Inject constructor(
    private val api: ProjectsApi,
    private val db: AppDatabase,
    private val syncManager: SyncManager,
    private val activeProjectDataStore: ActiveProjectDataStore,
    private val outboxDao: OutboxDao,
    private val tombstoneDao: TombstoneDao,
    private val outboxPusher: OutboxPusher, // <-- sync-пушер, чтобы пушить перед bootstrap
    @ApplicationContext private val appContext: Context
) : ProjectsRepository {

    private val bootstrapMutex = Mutex()
    private val isBootstrapping = AtomicBoolean(false)

//    private companion object {
//        const val MAX_PROJECTS = 3
//    }

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

        // если проектов совсем нет — создаём первый (не считаем это обходом лимита)
        val id = "draft-" + UUID.randomUUID().toString()
        val nowIso = TimeUtils.formatIso(TimeUtils.now())
        val defaultName = nextSequentialProjectName(all) // -> «Проект №1»
        db.projectDao().upsert(
            ProjectEntity(
                id = id,
                name = defaultName,
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
                has_local_changes = true
            )
        )
        activeProjectDataStore.setActiveProjectId(id)

        // поставить в outbox создание проекта
        outboxDao.insert(
            OutboxEntity(
                project_id = id, // проект ещё не серверный
                op_type = OutboxOpType.PROJECT_CREATE,
                payload_json = ProjectCreatePayload(
                    localId = id,
                    name = defaultName,
                    note = null
                ).toJson(),
                group_key = "project:create:$id"
            )
        )
        id
    }

    override suspend fun bootstrapFromRemote(): Int = withContext(Dispatchers.IO) {
        if (!isBootstrapping.compareAndSet(false, true)) return@withContext 0
        try {
            bootstrapMutex.withLock {
                var imported = 0

                // Сначала пушим локальные изменения (включая PROJECT_DELETE)
                runCatching { outboxPusher.pushAll() }.onFailure {
                    android.util.Log.w("Bootstrap", "pushAll failed: ${it.message}", it)
                }

                var cursor: String? = null
                val stateDao = db.projectLocalStateDao()

                do {
                    val page = api.listProjects(since = cursor, limit = 100)
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
        } catch (_: Throwable) {
            0
        } finally {
            isBootstrapping.set(false)
        }
    }

    override suspend fun createProject(name: String, note: String?): String = withContext(Dispatchers.IO) {
//        // --- ЛИМИТ 3 ПРОЕКТА ---
//        val activeCount = db.projectDao().countActive()
//        if (activeCount >= MAX_PROJECTS) {
//            throw IllegalStateException("Достигнут лимит: не более $MAX_PROJECTS проектов")
//        }

        // офлайн-first: создаём ЛОКАЛЬНО draft-*, сервер — через outbox
        val id = "draft-" + UUID.randomUUID().toString()
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

        // кладём в outbox PROJECT_CREATE
        outboxDao.insert(
            OutboxEntity(
                project_id = id,
                op_type = OutboxOpType.PROJECT_CREATE,
                payload_json = ProjectCreatePayload(
                    localId = id,
                    name = name,
                    note = note
                ).toJson(),
                group_key = "project:create:$id"
            )
        )

        // Активируем этот проект — UI сразу работает
        activeProjectDataStore.setActiveProjectId(id)

        // фоновая попытка пуша именно по этому проекту
        OutboxPushWorker.enqueueProject(appContext, id)

        id
    }

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

            // outbox PROJECT_UPDATE
            outboxDao.insert(
                OutboxEntity(
                    project_id = id,
                    op_type = OutboxOpType.PROJECT_UPDATE,
                    payload_json = ProjectUpdatePayload(
                        id = id,
                        name = name,
                        note = current?.note
                    ).toJson(),
                    group_key = "project:update:$id"
                )
            )

            // Точное таргетирование пуша
            OutboxPushWorker.enqueueProject(appContext, id)
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

            // tombstone — чтобы pull не вернул (если учитываешь при импорте)
            tombstoneDao.insert(
                TombstoneEntity(
                    project_id = id,
                    entity_type = TombstoneEntityType.PROJECT,
                    local_id = null,
                    server_uuid = if (!id.startsWith("draft-")) id else null
                )
            )

            // outbox PROJECT_DELETE — теперь реально обрабатывается пusher'ом
            outboxDao.insert(
                OutboxEntity(
                    project_id = id,
                    op_type = OutboxOpType.PROJECT_DELETE,
                    payload_json = ProjectDeletePayload(id = id).toJson(),
                    group_key = "project:delete:$id"
                )
            )

            // Если удаляем активный — ProjectsViewModel переключит; на всякий случай обнулим
           val active = activeProjectDataStore.activeProjectId.firstOrNull()
            if (active == id) {
                activeProjectDataStore.setActiveProjectId(null)
            }

            OutboxPushWorker.enqueueProject(appContext, id)

        }
    }

    override suspend fun openProject(id: String) {
        activeProjectDataStore.setActiveProjectId(id)
        // офлайн-first: syncManager сам сделает push→pull, когда сеть доступна
        runCatching { syncManager.syncProject(id) }
        OutboxPushWorker.enqueueProject(appContext, id)
    }

    // ---- ВСПОМОГАТЕЛЬНОЕ: вычисление «Проект №N» по локальным данным ----
    private fun nextSequentialProjectName(existing: List<ProjectEntity>): String {
        val re = Regex("""^Проект №(\d+)$""")
        val max = existing
            .asSequence()
            .mapNotNull { e -> re.find(e.name)?.groupValues?.getOrNull(1)?.toIntOrNull() }
            .maxOrNull() ?: 0
        return "Проект №${max + 1}"
    }
}