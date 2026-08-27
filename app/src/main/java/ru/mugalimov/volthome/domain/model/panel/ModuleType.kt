package ru.mugalimov.volthome.domain.model.panel

/**
 * Тип аппарата, который может быть отображен
 * на MVP-экране визуализации щита.
 */

enum class ModuleType {
    BREAKER,
    RCD,
    RCBO,
    VOLTAGE_RELAY,
    PHASE_CONTROL_RELAY,
    CURRENT_RELAY,
    MODULAR_CONTACTOR,
    SURGE_PROTECTION_DEVICE
}
