package ru.mugalimov.volthome.domain.policy.line

import ru.mugalimov.volthome.domain.model.GroupProfile
import ru.mugalimov.volthome.domain.policy.breaker.BreakerPolicyInput
import ru.mugalimov.volthome.domain.policy.breaker.BreakerPolicySelector
import ru.mugalimov.volthome.domain.policy.cable.CablePolicyInput
import ru.mugalimov.volthome.domain.policy.cable.CablePolicySelector

/**
 * Единый selector линии:
 * 1) сначала автомат
 * 2) потом кабель
 *
 * Важно:
 * - cable выбирается только после breaker;
 * - AUTO и MANUAL должны пользоваться только этим selector-ом;
 * - это pure domain selector, поэтому Hilt сюда не нужен.
 */
class LinePolicySelector(
    private val breakerPolicySelector: BreakerPolicySelector = BreakerPolicySelector(),
    private val cablePolicySelector: CablePolicySelector = CablePolicySelector()
) {

    fun select(input: LinePolicyInput): LinePolicyResult {
        // 1. Сначала выбираем автомат
        val breakerResult = breakerPolicySelector.select(
            BreakerPolicyInput(
                nominalCurrentA = input.nominalCurrentA,
                deviceType = input.deviceType,
                hasMotor = input.hasMotor
            )
        )

        // 2. Потом по уже выбранному автомату выбираем кабель
        val cableResult = cablePolicySelector.select(
            CablePolicyInput(
                breakerA = breakerResult.profile.breakerRating,
                deviceType = input.deviceType
            )
        )

        val profile = GroupProfile(
            maxCurrent = breakerResult.profile.maxCurrent,
            breakerRating = breakerResult.profile.breakerRating,
            cableSection = cableResult.cableSectionMm2,
            breakerType = breakerResult.profile.breakerType,
            whyBreakerSelected = breakerResult.reason,
            whyCableSelected = cableResult.reason
        )

        return LinePolicyResult(profile = profile)
    }
}