package ru.mugalimov.volthome.domain.policy.breaker

import ru.mugalimov.volthome.domain.model.DeviceType

/**
 * Вход policy selector-а.
 */
data class BreakerPolicyInput(
    val nominalCurrentA: Double,
    val deviceType: DeviceType,
    val hasMotor: Boolean
)