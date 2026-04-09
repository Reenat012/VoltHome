package ru.mugalimov.volthome.domain.policy.breaker

import ru.mugalimov.volthome.domain.model.GroupProfile
import ru.mugalimov.volthome.domain.model.LineSelectionReason

/**
 * Результат выбора автомата единым policy-слоем.
 */
data class BreakerPolicyResult(
    val profile: GroupProfile,
    val reason: LineSelectionReason
)