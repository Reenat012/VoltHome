package ru.mugalimov.volthome.domain.use_case

import javax.inject.Inject
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.catalog.AuxiliaryConnectionPoint
import ru.mugalimov.volthome.domain.model.catalog.SelectedApparatusSnapshot
import ru.mugalimov.volthome.domain.model.incomer.IncomerSpec
import ru.mugalimov.volthome.domain.model.panel.PanelLayoutSnapshot
import ru.mugalimov.volthome.domain.model.project.ProjectEngineeringSnapshot
import ru.mugalimov.volthome.domain.model.project.ProjectEngineeringWarning
import ru.mugalimov.volthome.domain.model.project.ProjectEngineeringWarningCode

class BuildProjectEngineeringSnapshotUseCase @Inject constructor(
    private val estimatePanelEquipmentCost: EstimatePanelEquipmentCostUseCase
) {
    operator fun invoke(
        projectId: String,
        phaseMode: PhaseMode,
        incomer: IncomerSpec,
        groups: List<CircuitGroup>,
        layout: PanelLayoutSnapshot,
        selections: Map<String, SelectedApparatusSnapshot>,
        nowEpochMs: Long = System.currentTimeMillis()
    ): ProjectEngineeringSnapshot {
        val groupById = groups.associateBy(CircuitGroup::groupId)
        val warnings = layout.customModules.mapNotNull { custom ->
            val connection = custom.apparatus.connection
            when {
                !connection.isDefined -> ProjectEngineeringWarning(
                    ProjectEngineeringWarningCode.AUXILIARY_CONNECTION_UNDEFINED,
                    custom.id,
                    "Для ${custom.designation} не задана точка электрического подключения"
                )
                connection.point == AuxiliaryConnectionPoint.GROUP &&
                    connection.groupId !in groupById -> ProjectEngineeringWarning(
                    ProjectEngineeringWarningCode.AUXILIARY_GROUP_NOT_FOUND,
                    custom.id,
                    "Линия для ${custom.designation} больше не существует"
                )
                connection.point == AuxiliaryConnectionPoint.GROUP &&
                    connection.phase != null &&
                    groupById[connection.groupId]?.phase != connection.phase ->
                    ProjectEngineeringWarning(
                        ProjectEngineeringWarningCode.AUXILIARY_PHASE_MISMATCH,
                        custom.id,
                        "Фаза ${custom.designation} не совпадает с фазой выбранной линии"
                    )
                else -> null
            }
        }
        return ProjectEngineeringSnapshot(
            projectId = projectId,
            phaseMode = phaseMode,
            incomer = incomer,
            groups = groups,
            panelLayout = layout,
            equipmentEstimate = estimatePanelEquipmentCost(
                incomer = incomer,
                groups = groups,
                selections = selections,
                customModules = layout.customModules
            ),
            warnings = warnings,
            createdAtEpochMs = nowEpochMs
        )
    }
}
