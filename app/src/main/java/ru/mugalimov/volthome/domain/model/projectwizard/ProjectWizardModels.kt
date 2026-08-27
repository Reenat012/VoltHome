package ru.mugalimov.volthome.domain.model.projectwizard

import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.RoomType
import ru.mugalimov.volthome.domain.model.create.RoomCreateRequest

enum class ProjectObjectType {
    APARTMENT,
    HOUSE,
    GARAGE_WORKSHOP,
    COMMERCIAL,
    CUSTOM
}

data class ProjectTemplateDevice(
    val deviceId: Long,
    val count: Int,
    val enabled: Boolean = true
)

data class ProjectTemplateRoom(
    val key: String,
    val title: String,
    val description: String,
    val roomType: RoomType,
    val defaultCount: Int = 1,
    val optional: Boolean = false,
    val devices: List<ProjectTemplateDevice>
)

data class ProjectTemplate(
    val id: String,
    val version: Int,
    val objectType: ProjectObjectType,
    val title: String,
    val subtitle: String,
    val recommendedPhaseMode: PhaseMode,
    val rooms: List<ProjectTemplateRoom>
)

data class CreateProjectFromTemplateRequest(
    val name: String,
    val objectType: ProjectObjectType,
    val phaseMode: PhaseMode,
    val inputPowerKw: Double?,
    val templateId: String?,
    val templateVersion: Int,
    val rooms: List<RoomCreateRequest>
)

data class CreatedProjectSummary(
    val projectId: String,
    val roomsCount: Int,
    val devicesCount: Int,
    val phaseMode: PhaseMode,
    val linesCount: Int,
    val installedPowerW: Double,
    val calculatedPowerW: Double,
    val inputPowerKw: Double?,
    val calculationCompleted: Boolean,
    val warnings: List<String> = emptyList()
)
