package ru.mugalimov.volthome.domain.model.manual

/**
 * In-memory manual edit session scoped to a single project.
 * baseState — снимок проекта на момент входа в manual.
 * draftState — изменяемый черновик.
 */
data class ManualEditSession(
    val projectId: String,
    val manualModeActive: Boolean,
    val baseState: ProjectEditState,
    val draftState: ProjectEditState,
    val version: Long,
    val updatedAtEpochMs: Long,
)