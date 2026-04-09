package ru.mugalimov.volthome.domain.policy.breaker

import javax.inject.Inject
import kotlin.math.ceil
import ru.mugalimov.volthome.domain.model.GroupProfile
import ru.mugalimov.volthome.domain.model.LineSelectionReason

/**
 * Единый selector выбора автомата для AUTO и MANUAL.
 *
 * Правила:
 * - requiredBreaker = ceil(current) с учётом floor по типу
 * - selectedBreaker = первый поддерживаемый номинал >= requiredBreaker
 * - D только для реально тяжёлых моторных случаев начиная с 25A
 * - motor-like, но не тяжёлый случай = C
 * - без мотора остаётся базовая кривая
 */
class BreakerPolicySelector @Inject constructor() {

    fun select(input: BreakerPolicyInput): BreakerPolicyResult {
        val flooredCurrentA = ceil(input.nominalCurrentA).toInt()
        val floorBreakerA = BreakerPolicyDefaults.floorByDeviceType(input.deviceType)
        val requiredBreakerA = maxOf(flooredCurrentA, floorBreakerA)

        val selectedBreakerA = BreakerPolicyDefaults.supportedNominalsA
            .firstOrNull { it >= requiredBreakerA }
            ?: throw IllegalArgumentException(
                "Нет поддерживаемого автомата для required=$requiredBreakerA A"
            )

        val baseCurve = when (selectedBreakerA) {
            10 -> BreakerCurve.B
            50, 63 -> BreakerCurve.D
            else -> BreakerCurve.C
        }

        val finalCurve = when {
            input.hasMotor && selectedBreakerA >= 25 -> BreakerCurve.D
            input.hasMotor -> BreakerCurve.C
            else -> baseCurve
        }

        val curveRule = when {
            input.hasMotor && selectedBreakerA >= 25 ->
                "motor_like_load_and_breaker_ge_25A -> D"
            input.hasMotor ->
                "motor_like_load_and_breaker_lt_25A -> C"
            else ->
                "non_motor_load -> base_curve_${baseCurve.name}"
        }

        val productRule =
            "required=max(ceil(current), floor_by_device_type), selected=first_supported_nominal_ge_required"

        val reason = LineSelectionReason(
            deviceType = input.deviceType,
            nominalCurrentA = input.nominalCurrentA,
            floorBreakerA = floorBreakerA,
            requiredBreakerA = requiredBreakerA,
            selectedBreakerA = selectedBreakerA,
            selectedCurve = finalCurve.name,
            curveRule = curveRule,
            productRule = productRule
        )

        val profile = GroupProfile(
            maxCurrent = selectedBreakerA.toDouble(),
            breakerRating = selectedBreakerA,
            cableSection = BreakerPolicyDefaults.defaultCableSectionByBreaker(selectedBreakerA),
            breakerType = finalCurve.name,
            whyBreakerSelected = reason
        )

        return BreakerPolicyResult(
            profile = profile,
            reason = reason
        )
    }
}