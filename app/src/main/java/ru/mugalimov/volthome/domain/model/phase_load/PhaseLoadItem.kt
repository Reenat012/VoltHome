package ru.mugalimov.volthome.domain.model.phase_load

import ru.mugalimov.volthome.domain.model.Phase

data class PhaseLoadItem(
    val phase: Phase, // A / B / C
    val totalPower: Double, // Вт
    val totalCurrent: Double, // А
    val groups: List<PhaseGroupItem>
)

