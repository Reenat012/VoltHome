package ru.mugalimov.volthome.domain.model.panel

/** Одна визуальная DIN-рейка щита. */
data class PanelRail(
    val number: Int,
    val capacityModuleUnits: Int,
    val assemblies: List<PanelAssembly>
) {
    val occupiedModuleUnits: Int
        get() = assemblies.sumOf { it.moduleUnits }

    val freeModuleUnits: Int
        get() = capacityModuleUnits - occupiedModuleUnits
}
