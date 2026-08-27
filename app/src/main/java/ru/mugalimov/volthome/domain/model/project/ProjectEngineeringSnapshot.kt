package ru.mugalimov.volthome.domain.model.project

import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.incomer.IncomerSpec
import ru.mugalimov.volthome.domain.model.panel.PanelLayoutSnapshot
import ru.mugalimov.volthome.domain.model.pricing.PanelEquipmentEstimate

/**
 * Согласованный снимок инженерного состояния. Экран, смета и экспорт должны
 * получать ручные аппараты из этого же снимка, а не собирать их независимо.
 */
data class ProjectEngineeringSnapshot(
    val projectId: String,
    val phaseMode: PhaseMode,
    val incomer: IncomerSpec,
    val groups: List<CircuitGroup>,
    val panelLayout: PanelLayoutSnapshot,
    val equipmentEstimate: PanelEquipmentEstimate,
    val warnings: List<ProjectEngineeringWarning>,
    val createdAtEpochMs: Long
)

data class ProjectEngineeringWarning(
    val code: ProjectEngineeringWarningCode,
    val apparatusId: String,
    val message: String
)

enum class ProjectEngineeringWarningCode {
    AUXILIARY_CONNECTION_UNDEFINED,
    AUXILIARY_GROUP_NOT_FOUND,
    AUXILIARY_PHASE_MISMATCH
}
