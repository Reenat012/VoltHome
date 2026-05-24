package ru.mugalimov.volthome.domain.model.singleline

import ru.mugalimov.volthome.domain.model.Phase

/**
 * Секция фазы на однолинейной схеме.
 *
 * Для однофазного проекта обычно используется секция фазы A.
 * Для трёхфазного проекта — отдельные секции A/B/C.
 */
data class SingleLinePhaseSection(
    val phase: Phase,
    val phaseBus: SingleLineBus,
    val totalCurrentAmps: Double?,
    val totalInstalledPowerWatts: Double?,
    val totalCalculatedPowerWatts: Double?,
    val groups: List<SingleLineGroupBlock>
)