package ru.mugalimov.volthome.domain.policy.cable

import javax.inject.Inject
import ru.mugalimov.volthome.domain.model.CableSelectionReason

/**
 * Единый selector кабеля для AUTO и MANUAL.
 *
 * Правила:
 * - кабель выбирается только после автомата;
 * - selectedSection >= normativeFloor;
 * - selectedSection >= productDefault;
 * - пока advanced override нет.
 */
class CablePolicySelector @Inject constructor() {

    fun select(input: CablePolicyInput): CablePolicyResult {
        val normativeFloor = CablePolicyDefaults.typeAwareFloorByBreaker(
            breakerA = input.breakerA,
            deviceType = input.deviceType
        )

        val productDefault = CablePolicyDefaults.productDefaultByBreaker(input.breakerA)

        val requiredSection = maxOf(normativeFloor, productDefault)

        val selectedSection = CablePolicyDefaults.supportedSectionsMm2
            .firstOrNull { it >= requiredSection }
            ?: throw IllegalArgumentException(
                "Нет поддерживаемого сечения для breaker=${input.breakerA}A requiredSection=$requiredSection"
            )

        val reason = CableSelectionReason(
            breakerA = input.breakerA,
            deviceType = input.deviceType,
            normativeFloorSectionMm2 = normativeFloor,
            productDefaultSectionMm2 = productDefault,
            selectedSectionMm2 = selectedSection,
            selectionRule = "selected=first_supported_section_ge_max(normative_floor, product_default)",
            productRule = "cable_selected_after_breaker"
        )

        return CablePolicyResult(
            cableSectionMm2 = selectedSection,
            reason = reason
        )
    }
}