package ru.mugalimov.volthome.domain.model.panel

import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.catalog.SelectedAuxiliaryApparatusSnapshot
import ru.mugalimov.volthome.domain.model.pricing.ProtectionDeviceSpec

/**
 * Один отображаемый аппарат в визуализации щита
 *
 * Размер задаётся в стандартных DIN-модулях. Позиция определяется порядком
 * аппаратов внутри [PanelAssembly], а положение на щите — раскладкой по рейкам.
 */

data class PanelModule(
    val id: String,
    val inventorySlotId: String,
    val type: ModuleType,
    val designation: String,
    val label: String,
    val nominalCurrent: Int?,
    val breakerCurve: String?,
    val leakageCurrent: Int?,
    val poles: Int,
    val moduleUnits: Int,
    val phase: Phase?,
    val priceSpec: ProtectionDeviceSpec?,
    val source: PanelModuleSource = PanelModuleSource.CALCULATED,
    val auxiliarySnapshot: SelectedAuxiliaryApparatusSnapshot? = null
) {
    /** Стабильный идентификатор отдельного физического аппарата в компоновке. */
    val layoutItemId: String
        get() = if (isUserAdded) {
            "custom:$inventorySlotId"
        } else {
            "calculated:$inventorySlotId"
        }

    val isUserAdded: Boolean
        get() = source == PanelModuleSource.USER_ADDED
}

enum class PanelModuleSource {
    CALCULATED,
    USER_ADDED
}
