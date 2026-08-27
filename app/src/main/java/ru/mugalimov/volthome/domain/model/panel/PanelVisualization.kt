package ru.mugalimov.volthome.domain.model.panel

/**
 * Доменная модель визуализации щита
 *
 * Результат локальной эскизной компоновки: каждый физический аппарат занимает
 * самостоятельную позицию, а электрические связи сохраняются в метаданных группы.
 */

data class PanelVisualization(
    val rails: List<PanelRail>,
    val groupsCount: Int
) {
    val occupiedModuleUnits: Int
        get() = rails.sumOf { it.occupiedModuleUnits }

    val totalModuleUnits: Int
        get() = rails.sumOf { it.capacityModuleUnits }

    val freeModuleUnits: Int
        get() = totalModuleUnits - occupiedModuleUnits
}
