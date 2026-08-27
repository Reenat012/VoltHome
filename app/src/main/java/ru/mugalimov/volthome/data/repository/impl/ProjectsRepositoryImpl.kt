package ru.mugalimov.volthome.data.repository.impl

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.data.local.datastore.ActiveProjectDataStore
import ru.mugalimov.volthome.data.local.entity.ProjectEntity
import ru.mugalimov.volthome.data.local.entity.ProjectLocalStateEntity
import ru.mugalimov.volthome.data.mapper.toDomainProject
import ru.mugalimov.volthome.data.repository.ProjectsRepository
import ru.mugalimov.volthome.di.database.AppDatabase
import ru.mugalimov.volthome.domain.model.Project
import ru.mugalimov.volthome.domain.model.DemoProjectSpec
import ru.mugalimov.volthome.util.TimeUtils

@Singleton
class ProjectsRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val activeProjectDataStore: ActiveProjectDataStore
) : ProjectsRepository {

    override fun listProjects(): Flow<List<Project>> =
        db.projectDao().observeAll().map { items -> items.map { it.toDomainProject() } }

    override suspend fun getActiveProjectId(): String? =
        activeProjectDataStore.activeProjectId.firstOrNull()

    override suspend fun countActiveProjects(): Int = withContext(Dispatchers.IO) {
        db.projectDao().observeAll().firstOrNull().orEmpty()
            .count { !DemoProjectSpec.isDemo(it.note) }
    }

    override suspend fun ensureActiveDraft(): String = withContext(Dispatchers.IO) {
        val current = activeProjectDataStore.activeProjectId.firstOrNull()
        if (!current.isNullOrBlank() && db.projectDao().getById(current) != null) {
            return@withContext current
        }

        val all = db.projectDao().observeAll().firstOrNull().orEmpty()
        val latest = all.maxByOrNull { it.updated_at }
        if (latest != null) {
            activeProjectDataStore.setActiveProjectId(latest.id)
            return@withContext latest.id
        }

        createLocalProject(nextSequentialProjectName(all), note = null)
    }

    override suspend fun createProject(name: String, note: String?, activate: Boolean): String =
        withContext(Dispatchers.IO) { createLocalProject(name, note, activate) }

    override suspend fun renameProject(id: String, name: String) = withContext(Dispatchers.IO) {
        val current = db.projectDao().getById(id) ?: return@withContext
        db.projectDao().rename(
            id = id,
            name = name,
            updatedAt = TimeUtils.formatIso(TimeUtils.now()),
            version = current.version
        )
    }

    override suspend fun deleteProject(id: String) = withContext(Dispatchers.IO) {
        if (db.projectDao().getById(id) == null) return@withContext
        // v29: все локальные данные проекта удаляются одной FK-каскадной операцией.
        db.projectDao().deleteById(id)

        if (activeProjectDataStore.activeProjectId.firstOrNull() == id) {
            activeProjectDataStore.setActiveProjectId(null)
        }
    }

    override suspend fun openProject(id: String) {
        activeProjectDataStore.setActiveProjectId(id)
    }

    private suspend fun createLocalProject(
        name: String,
        note: String?,
        activate: Boolean = true
    ): String {
        val id = UUID.randomUUID().toString()
        val now = TimeUtils.formatIso(TimeUtils.now())
        db.projectDao().upsert(
            ProjectEntity(
                id = id,
                name = name,
                note = note,
                version = 0,
                updated_at = now,
                is_deleted = false
            )
        )
        db.projectLocalStateDao().upsert(
            ProjectLocalStateEntity(
                project_id = id
            )
        )
        if (activate) activeProjectDataStore.setActiveProjectId(id)
        return id
    }

    private fun nextSequentialProjectName(existing: List<ProjectEntity>): String {
        val expression = Regex("""^Проект №(\d+)$""")
        val max = existing.asSequence()
            .mapNotNull { expression.find(it.name)?.groupValues?.getOrNull(1)?.toIntOrNull() }
            .maxOrNull() ?: 0
        return "Проект №${max + 1}"
    }
}
