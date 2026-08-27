package ru.mugalimov.volthome.domain.model.catalog

import ru.mugalimov.volthome.domain.model.pricing.ProtectionDeviceKind
import ru.mugalimov.volthome.domain.model.pricing.ProtectionDeviceSpec

/**
 * Консервативная электрическая проверка товарной позиции.
 *
 * Для автоматов больший номинал не считается безопасной заменой: он может
 * нарушить защиту уже рассчитанной линии. Для УЗО допускается больший
 * номинальный ток, потому что токовую защиту обеспечивает автомат.
 */
object ApparatusCompatibilityPolicy {

    fun evaluate(
        required: ProtectionDeviceSpec,
        product: ApparatusProduct
    ): CompatibilityDecision {
        val reasons = buildList {
            if (product.status == ProductStatus.ARCHIVED) {
                add(CompatibilityReasonCode.PRODUCT_ARCHIVED)
            }
            if (product.function != required.kind || product.spec.kind != required.kind) {
                add(CompatibilityReasonCode.FUNCTION_MISMATCH)
            }
            if (product.spec.poles != required.poles) {
                add(CompatibilityReasonCode.POLES_MISMATCH)
            }

            when (required.kind) {
                ProtectionDeviceKind.MCB,
                ProtectionDeviceKind.RCBO -> if (product.spec.ratedCurrentA != required.ratedCurrentA) {
                    add(CompatibilityReasonCode.RATED_CURRENT_MISMATCH)
                }

                ProtectionDeviceKind.RCD -> if (product.spec.ratedCurrentA < required.ratedCurrentA) {
                    add(CompatibilityReasonCode.RATED_CURRENT_BELOW_REQUIRED)
                }
            }

            if (required.breakerCurve != null &&
                !product.spec.breakerCurve.equals(required.breakerCurve, ignoreCase = true)
            ) {
                add(CompatibilityReasonCode.BREAKER_CURVE_MISMATCH)
            }
            if ((product.spec.breakingCapacityA ?: 0) < (required.breakingCapacityA ?: 0)) {
                add(CompatibilityReasonCode.BREAKING_CAPACITY_BELOW_REQUIRED)
            }
            if (required.leakageCurrentMa != null &&
                product.spec.leakageCurrentMa != required.leakageCurrentMa
            ) {
                add(CompatibilityReasonCode.LEAKAGE_CURRENT_MISMATCH)
            }
            if (required.rcdType != null && product.spec.rcdType != required.rcdType) {
                add(CompatibilityReasonCode.RCD_TYPE_MISMATCH)
            }
            if (product.spec.selectivity != required.selectivity) {
                add(CompatibilityReasonCode.SELECTIVITY_MISMATCH)
            }
        }.distinct()

        val hardFailures = setOf(
            CompatibilityReasonCode.FUNCTION_MISMATCH,
            CompatibilityReasonCode.POLES_MISMATCH,
            CompatibilityReasonCode.RATED_CURRENT_MISMATCH,
            CompatibilityReasonCode.RATED_CURRENT_BELOW_REQUIRED,
            CompatibilityReasonCode.BREAKER_CURVE_MISMATCH,
            CompatibilityReasonCode.BREAKING_CAPACITY_BELOW_REQUIRED,
            CompatibilityReasonCode.LEAKAGE_CURRENT_MISMATCH,
            CompatibilityReasonCode.RCD_TYPE_MISMATCH,
            CompatibilityReasonCode.SELECTIVITY_MISMATCH
        )

        return CompatibilityDecision(
            result = when {
                reasons.any(hardFailures::contains) -> ApparatusCompatibility.INCOMPATIBLE
                reasons.isNotEmpty() -> ApparatusCompatibility.COMPATIBLE_WITH_DIFFERENCES
                product.spec == required -> ApparatusCompatibility.EXACT
                else -> ApparatusCompatibility.COMPATIBLE_WITH_DIFFERENCES
            },
            reasonCodes = reasons
        )
    }
}
