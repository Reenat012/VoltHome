package ru.mugalimov.volthome.domain.model.panel

import ru.mugalimov.volthome.domain.model.catalog.SelectedAuxiliaryApparatusSnapshot

/**
 * Проектный снимок физической DIN-компоновки.
 *
 * Расчётные параметры в нём не хранятся: после пересчёта аппараты сопоставляются
 * по стабильным inventorySlotId, а пользовательские аппараты остаются на месте.
 */
data class PanelLayoutSnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val enclosure: PanelEnclosureConfig = PanelEnclosureConfig(),
    val rails: List<PanelLayoutRailSnapshot>,
    val customModules: List<PanelCustomModuleSnapshot> = emptyList(),
    val baseFingerprint: String,
    val updatedAtEpochMs: Long
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 3
        const val DEFAULT_RAIL_CAPACITY = 12
    }

    /** Временный совместимый accessor для UI этапа 1. */
    val railCapacityModuleUnits: Int
        get() = enclosure.modulesPerRail
}

data class PanelLayoutRailSnapshot(
    val id: String,
    /** Порядок отдельных физических DIN-аппаратов, а не групповых связок. */
    val itemIds: List<String>
)

data class PanelEnclosureConfig(
    val modulesPerRail: Int = PanelLayoutSnapshot.DEFAULT_RAIL_CAPACITY,
    val railCount: Int = 1
) {
    init {
        require(modulesPerRail in MIN_MODULES_PER_RAIL..MAX_MODULES_PER_RAIL)
        require(railCount in MIN_RAIL_COUNT..MAX_RAIL_COUNT)
    }

    val totalModuleUnits: Int
        get() = modulesPerRail * railCount

    companion object {
        const val MIN_MODULES_PER_RAIL = 4
        const val MAX_MODULES_PER_RAIL = 72
        const val MIN_RAIL_COUNT = 1
        const val MAX_RAIL_COUNT = 12
    }
}

data class PanelCustomModuleSnapshot(
    val id: String,
    val designation: String,
    val apparatus: SelectedAuxiliaryApparatusSnapshot
) {
    val blockId: String
        get() = "custom:$id"
}

enum class PanelMoveDirection {
    LEFT,
    RIGHT,
    UP,
    DOWN
}
