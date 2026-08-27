package ru.mugalimov.volthome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mugalimov.volthome.domain.model.CalculationSource
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.incomer.IncomerKind
import ru.mugalimov.volthome.domain.model.incomer.RcdSelectivity
import ru.mugalimov.volthome.domain.model.incomer.RcdType
import ru.mugalimov.volthome.domain.model.incomer.IncomerSpec
import ru.mugalimov.volthome.domain.model.panel.ModuleType
import ru.mugalimov.volthome.domain.model.protection.RcdKind
import ru.mugalimov.volthome.domain.model.protection.RcdSpec
import ru.mugalimov.volthome.domain.use_case.GeneratePanelVisualizationUseCase
import ru.mugalimov.volthome.domain.use_case.ProtectionDeviceInventory

class GeneratePanelVisualizationUseCaseTest {

    private val useCase = GeneratePanelVisualizationUseCase()

    @Test
    fun `incomer kind creates matching physical apparatus`() {
        val cases = listOf(
            IncomerKind.MCB_ONLY to listOf(ModuleType.BREAKER),
            IncomerKind.MCB_PLUS_RCD to listOf(ModuleType.BREAKER, ModuleType.RCD),
            IncomerKind.RCBO to listOf(ModuleType.RCBO)
        )

        cases.forEach { (kind, expectedTypes) ->
            val panel = useCase(
                incomer = incomer(kind = kind),
                groups = emptyList()
            )

            val inputAssembly = panel.rails.single().assemblies.single()
            assertEquals(expectedTypes, inputAssembly.modules.map { it.type })
            assertNull(inputAssembly.group)
        }
    }

    @Test
    fun `protected group creates linked rcd and breaker assembly`() {
        val panel = useCase(
            incomer = null,
            groups = listOf(group(number = 7, rcdRequired = true, phase = Phase.B))
        )

        val assembly = panel.rails.single().assemblies.single()
        assertEquals(listOf(ModuleType.RCD, ModuleType.BREAKER), assembly.modules.map { it.type })
        assertEquals(3, assembly.moduleUnits)
        assertEquals(Phase.B, assembly.group?.phase)
        assertEquals(7, assembly.group?.number)
    }

    @Test
    fun `linked group protection is never split between rails`() {
        val groups = (1..3).map { number ->
            group(number = number, rcdRequired = true)
        }

        val panel = useCase(
            incomer = incomer(
                kind = IncomerKind.MCB_ONLY,
                poles = 4
            ),
            groups = groups
        )

        assertEquals(2, panel.rails.size)
        assertEquals(listOf("incomer", "group-1", "group-2"), panel.rails[0].assemblies.map { it.id })
        assertEquals(listOf("group-3"), panel.rails[1].assemblies.map { it.id })
        assertEquals(listOf(ModuleType.RCD, ModuleType.BREAKER), panel.rails[1].assemblies.single().modules.map { it.type })
    }

    @Test
    fun `three phase group renders three pole breaker and four pole rcd`() {
        val threePhaseGroup = group(
            number = 9,
            rcdRequired = true,
            phase = Phase.THREE_PHASE
        ).copy(
            rcdSpec = RcdSpec(
                kind = RcdKind.RCD,
                ratedCurrentA = 25,
                leakageCurrentMa = 30,
                type = RcdType.A,
                poles = 4,
                selectivity = RcdSelectivity.NONE,
                source = CalculationSource.AUTO
            )
        )

        val assembly = useCase(
            incomer = null,
            groups = listOf(threePhaseGroup)
        ).rails.single().assemblies.single()

        assertEquals(listOf(4, 3), assembly.modules.map { it.poles })
        assertEquals(listOf(25, 16), assembly.modules.map { it.nominalCurrent })
        assertEquals(7, assembly.moduleUnits)
    }

    @Test
    fun `group rcbo is rendered as one physical apparatus`() {
        val rcboGroup = group(number = 4, rcdRequired = true).copy(
            rcdSpec = RcdSpec(
                kind = RcdKind.RCBO,
                ratedCurrentA = 16,
                leakageCurrentMa = 30,
                type = RcdType.A,
                poles = 2,
                selectivity = RcdSelectivity.NONE,
                source = CalculationSource.AUTO
            )
        )

        val assembly = useCase(null, listOf(rcboGroup)).rails.single().assemblies.single()

        assertEquals(listOf(ModuleType.RCBO), assembly.modules.map { it.type })
        assertEquals(2, assembly.moduleUnits)
    }

    @Test
    fun `every visualized apparatus uses the same pricing spec as estimate inventory`() {
        val incomer = incomer(IncomerKind.MCB_PLUS_RCD)
        val groups = listOf(group(1, rcdRequired = true), group(2))
        val expectedSpecs = ProtectionDeviceInventory.build(incomer, groups)
            .associate { it.id to it.spec }

        val modules = useCase(incomer, groups).rails
            .flatMap { it.assemblies }
            .flatMap { it.modules }

        assertTrue(modules.isNotEmpty())
        modules.forEach { module ->
            assertEquals(expectedSpecs.getValue(module.inventorySlotId), module.priceSpec)
        }
    }

    @Test
    fun `group order and calculated values are preserved`() {
        val panel = useCase(
            incomer = null,
            groups = listOf(
                group(number = 3, phase = Phase.C),
                group(number = 1, phase = Phase.A)
            )
        )

        val assemblies = panel.rails.flatMap { it.assemblies }
        assertEquals(listOf(1, 3), assemblies.mapNotNull { it.group?.number })
        assertEquals(listOf(Phase.A, Phase.C), assemblies.mapNotNull { it.group?.phase })
        assertEquals(2.5, assemblies.first().group?.cableSection ?: 0.0, 0.0)
        assertEquals(16, assemblies.first().modules.single().nominalCurrent)
    }

    private fun incomer(
        kind: IncomerKind,
        poles: Int = 2
    ): IncomerSpec {
        return IncomerSpec(
            kind = kind,
            poles = poles,
            mcbRating = 40,
            mcbCurve = "C",
            icn = 6_000,
            rcdSensitivityMa = if (kind == IncomerKind.MCB_ONLY) null else 100
        )
    }

    private fun group(
        number: Int,
        rcdRequired: Boolean = false,
        phase: Phase = Phase.A
    ): CircuitGroup {
        return CircuitGroup(
            groupId = number.toLong(),
            groupNumber = number,
            roomName = "Комната $number",
            roomId = number.toLong(),
            groupType = DeviceType.SOCKET,
            devices = emptyList(),
            nominalCurrent = 7.0,
            installedPowerW = 1_500,
            circuitBreaker = 16,
            cableSection = 2.5,
            breakerType = "C",
            rcdRequired = rcdRequired,
            rcdCurrent = 30,
            phase = phase
        )
    }
}
