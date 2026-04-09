package ru.mugalimov.volthome.domain.policy.cable

import ru.mugalimov.volthome.domain.model.DeviceType

/**
 * Вход selector-а кабеля.
 *
 * Важно:
 * - breaker уже должен быть выбран выше.
 */
data class CablePolicyInput(
    val breakerA: Int,
    val deviceType: DeviceType
)