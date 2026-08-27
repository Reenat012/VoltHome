package ru.mugalimov.volthome.data.repository.impl

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.mugalimov.volthome.data.local.dao.ProjectSetupDao
import ru.mugalimov.volthome.data.local.entity.ProjectSetupEntity
import ru.mugalimov.volthome.data.repository.ProjectSetup
import ru.mugalimov.volthome.data.repository.ProjectSetupRepository
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.projectwizard.ProjectObjectType

@Singleton
class ProjectSetupRepositoryImpl @Inject constructor(
    private val dao: ProjectSetupDao
) : ProjectSetupRepository {
    override suspend fun get(projectId: String): ProjectSetup? = dao.get(projectId)?.toDomain()

    override fun observe(projectId: String): Flow<ProjectSetup?> =
        dao.observe(projectId).map { it?.toDomain() }

    override suspend fun save(setup: ProjectSetup) {
        dao.upsert(setup.toEntity())
    }

    override suspend fun getOrCreate(
        projectId: String,
        fallbackPhaseMode: PhaseMode
    ): ProjectSetup {
        get(projectId)?.let { return it }
        return ProjectSetup(
            projectId = projectId,
            objectType = ProjectObjectType.CUSTOM,
            phaseMode = fallbackPhaseMode,
            inputPowerKw = null,
            sourceTemplateId = null,
            sourceTemplateVersion = 1,
            wizardCompleted = false
        ).also { save(it) }
    }

    override suspend fun updatePhaseMode(projectId: String, phaseMode: PhaseMode) {
        val updated = dao.updatePhaseMode(projectId, phaseMode.name)
        if (updated == 0) {
            save(getOrCreate(projectId, phaseMode).copy(phaseMode = phaseMode))
        }
    }

    private fun ProjectSetupEntity.toDomain() = ProjectSetup(
        projectId = project_id,
        objectType = runCatching { ProjectObjectType.valueOf(object_type) }
            .getOrDefault(ProjectObjectType.CUSTOM),
        phaseMode = runCatching { PhaseMode.valueOf(phase_mode) }.getOrDefault(PhaseMode.THREE),
        inputPowerKw = input_power_kw,
        sourceTemplateId = source_template_id,
        sourceTemplateVersion = source_template_version,
        wizardCompleted = wizard_completed
    )

    private fun ProjectSetup.toEntity() = ProjectSetupEntity(
        project_id = projectId,
        object_type = objectType.name,
        phase_mode = phaseMode.name,
        input_power_kw = inputPowerKw,
        source_template_id = sourceTemplateId,
        source_template_version = sourceTemplateVersion,
        wizard_completed = wizardCompleted
    )
}
