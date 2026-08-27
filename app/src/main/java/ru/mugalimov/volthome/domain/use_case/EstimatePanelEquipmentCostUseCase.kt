package ru.mugalimov.volthome.domain.use_case

import javax.inject.Inject
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.catalog.AuxiliaryApparatusKind
import ru.mugalimov.volthome.domain.model.catalog.SelectedApparatusSnapshot
import ru.mugalimov.volthome.domain.model.incomer.IncomerSpec
import ru.mugalimov.volthome.domain.model.panel.PanelCustomModuleSnapshot
import ru.mugalimov.volthome.domain.model.pricing.AuxiliaryEstimateLine
import ru.mugalimov.volthome.domain.model.pricing.MoneyRange
import ru.mugalimov.volthome.domain.model.pricing.PanelEquipmentEstimate

class EstimatePanelEquipmentCostUseCase @Inject constructor(
    private val estimateProtectionCost: EstimateProtectionCostUseCase
) {
    operator fun invoke(
        incomer: IncomerSpec,
        groups: List<CircuitGroup>,
        selections: Map<String, SelectedApparatusSnapshot> = emptyMap(),
        customModules: List<PanelCustomModuleSnapshot> = emptyList()
    ): PanelEquipmentEstimate {
        val protection = estimateProtectionCost(incomer, groups, selections)
        val auxiliary = customModules.map { module ->
            val apparatus = module.apparatus
            AuxiliaryEstimateLine(
                customModuleId = module.id,
                designation = module.designation,
                title = apparatus.displayName,
                functionLabel = apparatus.function.displayLabel(),
                price = apparatus.effectivePrice,
                isUserPrice = apparatus.userPriceKopecks != null,
                connectionDefined = apparatus.connection.isDefined
            )
        }
        val total = auxiliary.fold(protection.total) { acc, line -> acc + line.price }
        return PanelEquipmentEstimate(protection, auxiliary, total)
    }

    private fun AuxiliaryApparatusKind.displayLabel(): String = when (this) {
        AuxiliaryApparatusKind.VOLTAGE_RELAY -> "Реле напряжения"
        AuxiliaryApparatusKind.PHASE_CONTROL_RELAY -> "Реле контроля фаз"
        AuxiliaryApparatusKind.CURRENT_RELAY -> "Реле тока"
        AuxiliaryApparatusKind.MODULAR_CONTACTOR -> "Модульный контактор"
        AuxiliaryApparatusKind.SURGE_PROTECTION_DEVICE -> "УЗИП"
    }
}

private operator fun MoneyRange.plus(other: MoneyRange): MoneyRange = MoneyRange(
    minKopecks = minKopecks + other.minKopecks,
    typicalKopecks = typicalKopecks + other.typicalKopecks,
    maxKopecks = maxKopecks + other.maxKopecks
)
