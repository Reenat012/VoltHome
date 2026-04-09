package ru.mugalimov.volthome.domain.policy.line

import ru.mugalimov.volthome.domain.model.DeviceType

/**
 * Единый вход полного line policy.
 *
 * Сначала выбирается автомат, потом кабель.
 */
data class LinePolicyInput(
    val nominalCurrentA: Double,
    val deviceType: DeviceType,
    val hasMotor: Boolean
)