package ru.mugalimov.volthome.domain.use_case

import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.cable.CableLineCalculation
import ru.mugalimov.volthome.domain.model.incomer.IncomerKind
import ru.mugalimov.volthome.domain.model.incomer.IncomerSpec
import ru.mugalimov.volthome.domain.model.panel.ModuleType
import ru.mugalimov.volthome.domain.model.panel.PanelAssembly
import ru.mugalimov.volthome.domain.model.panel.PanelGroup
import ru.mugalimov.volthome.domain.model.panel.PanelModule
import ru.mugalimov.volthome.domain.model.panel.PanelRail
import ru.mugalimov.volthome.domain.model.panel.PanelVisualization
import ru.mugalimov.volthome.domain.model.pricing.ProtectionDeviceSpec
import ru.mugalimov.volthome.domain.model.protection.RcdKind
import javax.inject.Inject

/**
 * Собирает локальную эскизную компоновку щита из уже рассчитанной экспликации.
 *
 * Use-case не меняет расчётные решения. Он только переводит готовые данные в
 * визуальные аппараты стандартной модульной ширины и размещает связанные
 * аппараты по DIN-рейкам, не разрывая связки.
 */
class GeneratePanelVisualizationUseCase @Inject constructor() {

    operator fun invoke(
        incomer: IncomerSpec?,
        groups: List<CircuitGroup>,
        cableCalculations: Map<Long, CableLineCalculation> = emptyMap()
    ): PanelVisualization {
        val priceSpecsById = ProtectionDeviceInventory.build(incomer, groups)
            .associate { it.id to it.spec }
        val assemblies = buildList {
            incomer?.let { add(it.toPanelAssembly(priceSpecsById)) }
            groups
                .sortedBy { it.groupNumber }
                .forEach { group ->
                    add(group.toPanelAssembly(priceSpecsById, cableCalculations[group.groupId]))
                }
        }

        return PanelVisualization(
            rails = assemblies.layoutOnRails(),
            groupsCount = groups.size
        )
    }

    private fun IncomerSpec.toPanelAssembly(priceSpecsById: Map<String, ProtectionDeviceSpec>): PanelAssembly {
        val modules = when (kind) {
            IncomerKind.MCB_ONLY -> listOf(
                breakerModule(
                    id = "incomer-breaker",
                    designation = "QF0",
                    label = "Вводной автомат",
                    priceSpec = priceSpecsById.getValue("incomer-breaker")
                )
            )

            IncomerKind.MCB_PLUS_RCD -> listOf(
                breakerModule(
                    id = "incomer-breaker",
                    designation = "QF0",
                    label = "Вводной автомат",
                    priceSpec = priceSpecsById.getValue("incomer-breaker")
                ),
                PanelModule(
                    id = "incomer-rcd",
                    inventorySlotId = "incomer-rcd",
                    type = ModuleType.RCD,
                    designation = "QD0",
                    label = "Вводное УЗО",
                    nominalCurrent = rcdRatedCurrentA ?: mcbRating,
                    breakerCurve = null,
                    leakageCurrent = rcdSensitivityMa,
                    poles = poles,
                    moduleUnits = poles,
                    phase = null,
                    priceSpec = priceSpecsById.getValue("incomer-rcd")
                )
            )

            IncomerKind.RCBO -> listOf(
                PanelModule(
                    id = "incomer-rcbo",
                    inventorySlotId = "incomer-rcbo",
                    type = ModuleType.RCBO,
                    designation = "QFD0",
                    label = "Вводной дифавтомат",
                    nominalCurrent = mcbRating,
                    breakerCurve = mcbCurve,
                    leakageCurrent = rcdSensitivityMa,
                    poles = poles,
                    moduleUnits = poles,
                    phase = null,
                    priceSpec = priceSpecsById.getValue("incomer-rcbo")
                )
            )
        }

        return PanelAssembly(
            id = "incomer",
            title = "Ввод",
            modules = modules,
            group = null
        )
    }

    private fun IncomerSpec.breakerModule(
        id: String,
        designation: String,
        label: String,
        priceSpec: ProtectionDeviceSpec
    ): PanelModule {
        return PanelModule(
            id = id,
            inventorySlotId = id,
            type = ModuleType.BREAKER,
            designation = designation,
            label = label,
            nominalCurrent = mcbRating,
            breakerCurve = mcbCurve,
            leakageCurrent = null,
            poles = poles,
            moduleUnits = poles,
            phase = null,
            priceSpec = priceSpec
        )
    }

    private fun CircuitGroup.toPanelAssembly(
        priceSpecsById: Map<String, ProtectionDeviceSpec>,
        cableCalculation: CableLineCalculation?
    ): PanelAssembly {
        val panelGroup = PanelGroup(
            id = groupId,
            number = groupNumber,
            roomName = roomName,
            type = groupType,
            cableSection = cableCalculation?.cable?.phaseSectionMm2 ?: cableSection,
            cableLabel = cableCalculation?.cable?.compactLabel
                ?: "${cableSection.toCableSectionLabel()} мм²",
            cableStatus = cableCalculation?.status,
            voltageDropPercent = cableCalculation?.voltageDropPercent,
            correctedAmpacityA = cableCalculation?.correctedAmpacityA,
            phase = phase,
            installedPowerW = installedPowerW,
            installedCurrentA = CircuitLoadCalculator.calculate(devices).installedCurrentA,
            circuitBreaker = circuitBreaker,
            breakerType = breakerType,
            rcdRequired = rcdRequired,
            rcdCurrent = rcdCurrent,
            rcdReasonCodes = rcdReasonCodes,
            calculationSource = calculationSource,
            algorithmVersion = algorithmVersion,
            deviceNames = devices.map { it.name }
        )

        val breakerPoles = if (phase == ru.mugalimov.volthome.domain.model.Phase.THREE_PHASE) 3 else 1
        val visualPrefix = "group-$groupNumber"
        val inventoryPrefix = ProtectionDeviceInventory.groupSlotPrefix(this)
        val breaker = if (rcdSpec?.kind != RcdKind.RCBO) PanelModule(
            id = "$visualPrefix-breaker",
            inventorySlotId = "$inventoryPrefix-breaker",
            type = ModuleType.BREAKER,
            designation = "QF$groupNumber",
            label = "Автомат группы $groupNumber",
            nominalCurrent = circuitBreaker,
            breakerCurve = breakerType,
            leakageCurrent = null,
            poles = breakerPoles,
            moduleUnits = breakerPoles,
            phase = phase,
            priceSpec = priceSpecsById.getValue("$inventoryPrefix-breaker")
        ) else null

        val modules = if (rcdSpec?.kind == RcdKind.RCBO) {
            listOf(
                PanelModule(
                    id = "$visualPrefix-rcbo",
                    inventorySlotId = "$inventoryPrefix-rcbo",
                    type = ModuleType.RCBO,
                    designation = "QFD$groupNumber",
                    label = "Дифавтомат группы $groupNumber",
                    nominalCurrent = rcdSpec.ratedCurrentA ?: circuitBreaker,
                    breakerCurve = breakerType,
                    leakageCurrent = rcdSpec.leakageCurrentMa,
                    poles = rcdSpec.poles,
                    moduleUnits = rcdSpec.poles,
                    phase = phase,
                    priceSpec = priceSpecsById.getValue("$inventoryPrefix-rcbo")
                )
            )
        } else if (rcdRequired) {
            listOf(
                PanelModule(
                    id = "$visualPrefix-rcd",
                    inventorySlotId = "$inventoryPrefix-rcd",
                    type = ModuleType.RCD,
                    designation = "QD$groupNumber",
                    label = "УЗО группы $groupNumber",
                    nominalCurrent = rcdSpec?.ratedCurrentA ?: circuitBreaker,
                    breakerCurve = null,
                    leakageCurrent = rcdCurrent,
                    poles = rcdSpec?.poles
                        ?: if (phase == ru.mugalimov.volthome.domain.model.Phase.THREE_PHASE) 4 else 2,
                    moduleUnits = rcdSpec?.poles
                        ?: if (phase == ru.mugalimov.volthome.domain.model.Phase.THREE_PHASE) 4 else 2,
                    phase = phase,
                    priceSpec = priceSpecsById.getValue("$inventoryPrefix-rcd")
                ),
                requireNotNull(breaker)
            )
        } else {
            listOf(requireNotNull(breaker))
        }

        return PanelAssembly(
            id = "group-$groupNumber",
            title = panelGroup.shortLabel,
            modules = modules,
            group = panelGroup
        )
    }

    private fun List<PanelAssembly>.layoutOnRails(): List<PanelRail> {
        if (isEmpty()) return emptyList()

        val result = mutableListOf<PanelRail>()
        var currentAssemblies = mutableListOf<PanelAssembly>()
        var occupiedUnits = 0

        fun flushRail() {
            if (currentAssemblies.isEmpty()) return

            result += PanelRail(
                number = result.size + 1,
                capacityModuleUnits = RAIL_CAPACITY_MODULE_UNITS,
                assemblies = currentAssemblies.toList()
            )
            currentAssemblies = mutableListOf()
            occupiedUnits = 0
        }

        forEach { assembly ->
            require(assembly.moduleUnits <= RAIL_CAPACITY_MODULE_UNITS) {
                "Assembly ${assembly.id} does not fit on a DIN rail"
            }

            if (currentAssemblies.isNotEmpty() &&
                occupiedUnits + assembly.moduleUnits > RAIL_CAPACITY_MODULE_UNITS
            ) {
                flushRail()
            }

            currentAssemblies += assembly
            occupiedUnits += assembly.moduleUnits
        }

        flushRail()
        return result
    }

    private fun Double.toCableSectionLabel(): String =
        if (this % 1.0 == 0.0) toInt().toString() else toString()

    companion object {
        const val RAIL_CAPACITY_MODULE_UNITS = 12

    }
}
