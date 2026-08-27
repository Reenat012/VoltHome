package ru.mugalimov.volthome.domain.model.panel

/** Визуальный контейнер. В редакторе v2 один контейнер соответствует одному аппарату. */
data class PanelAssembly(
    val id: String,
    val title: String,
    val modules: List<PanelModule>,
    val group: PanelGroup?
) {
    val moduleUnits: Int
        get() = modules.sumOf { it.moduleUnits }

    /** Стабильный ключ позиции для сохранения пользовательской раскладки. */
    val layoutBlockId: String
        get() = modules.singleOrNull()?.layoutItemId
            ?: "calculated:" + modules.joinToString("+") { it.inventorySlotId }

    val isUserAdded: Boolean
        get() = modules.all { it.isUserAdded }
}
