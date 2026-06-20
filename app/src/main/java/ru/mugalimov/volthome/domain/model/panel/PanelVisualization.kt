package ru.mugalimov.volthome.domain.model.panel

/**
 * Доменная модель визуализации щита
 *
 * Важно:
 * эта модель описывает только логическую структуру визуализации,
 * без координат, размеров, реек и UI-позиционирования
 */

data class PanelVisualization(
    val incomer: PanelModule?,
    val modules: List<PanelModule>
)
