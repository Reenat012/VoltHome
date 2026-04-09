package ru.mugalimov.volthome.domain.policy.line

import ru.mugalimov.volthome.domain.model.GroupProfile

/**
 * Результат полного выбора линии.
 */
data class LinePolicyResult(
    val profile: GroupProfile
)