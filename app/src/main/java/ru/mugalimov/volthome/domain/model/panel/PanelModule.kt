package ru.mugalimov.volthome.domain.model.panel

/**
 * Один отображаемый аппарат в визуализации щита
 *
 * Важно:
 * модель не содержит реальные размеры модуля,
 * координаты, позицию на рейке и список устройств группы.
 */

data class PanelModule(
    val type: ModuleType,
    val label: String,
    val nominalCurrent: Int?,
    val breakerCurve: String?,
    val leakageCurrent: Int?,
    val group: PanelGroup?
)
