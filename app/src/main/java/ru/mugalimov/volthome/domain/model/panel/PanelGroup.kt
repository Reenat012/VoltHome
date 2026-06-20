package ru.mugalimov.volthome.domain.model.panel

/**
 * Группа, связанная с апппартом защиты
 * в визуализации щита
 *
 * Важно:
 * внутри MVP не храним список устройств,
 * фазу, координаты и монтажные параметры
 */

data class PanelGroup(
    val name: String,
    val cableSelection: Double?
)
