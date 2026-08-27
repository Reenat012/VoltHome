package ru.mugalimov.volthome.data.repository

import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.projectwizard.ProjectObjectType

data class ProjectSetup(
    val projectId: String,
    val objectType: ProjectObjectType,
    val phaseMode: PhaseMode,
    val inputPowerKw: Double?,
    val sourceTemplateId: String?,
    val sourceTemplateVersion: Int,
    val wizardCompleted: Boolean
)

interface ProjectSetupRepository {
    suspend fun get(projectId: String): ProjectSetup?
    fun observe(projectId: String): Flow<ProjectSetup?>
    suspend fun save(setup: ProjectSetup)
    suspend fun getOrCreate(projectId: String, fallbackPhaseMode: PhaseMode): ProjectSetup
    suspend fun updatePhaseMode(projectId: String, phaseMode: PhaseMode)
}
