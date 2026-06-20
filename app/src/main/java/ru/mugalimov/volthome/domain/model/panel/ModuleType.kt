package ru.mugalimov.volthome.domain.model.panel

/**
 * Тип аппарата, который может быть отображен
 * на MVP-экране визуализации щита.
 */

enum class ModuleType {
    INCOMER,
    BREAKER,
    RCD,
    RCBO
}