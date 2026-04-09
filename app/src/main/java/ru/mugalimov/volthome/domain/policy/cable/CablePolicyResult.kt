package ru.mugalimov.volthome.domain.policy.cable

import ru.mugalimov.volthome.domain.model.CableSelectionReason

/**
 * Результат выбора кабеля.
 */
data class CablePolicyResult(
    val cableSectionMm2: Double,
    val reason: CableSelectionReason
)