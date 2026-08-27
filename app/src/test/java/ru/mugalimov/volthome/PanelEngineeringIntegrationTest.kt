package ru.mugalimov.volthome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.catalog.AuxiliaryApparatusKind
import ru.mugalimov.volthome.domain.model.catalog.AuxiliaryConnectionPoint
import ru.mugalimov.volthome.domain.model.catalog.AuxiliaryElectricalConnection
import ru.mugalimov.volthome.domain.model.catalog.SelectedAuxiliaryApparatusSnapshot
import ru.mugalimov.volthome.domain.model.incomer.IncomerKind
import ru.mugalimov.volthome.domain.model.incomer.IncomerSpec
import ru.mugalimov.volthome.domain.model.panel.PanelCustomModuleSnapshot
import ru.mugalimov.volthome.domain.model.pricing.MoneyRange
import ru.mugalimov.volthome.domain.model.pricing.ProtectionDeviceKind
import ru.mugalimov.volthome.domain.model.pricing.ProtectionPriceCatalog
import ru.mugalimov.volthome.domain.model.pricing.ProtectionPriceEntry
import ru.mugalimov.volthome.domain.model.provider.ProtectionPriceCatalogProvider
import ru.mugalimov.volthome.domain.model.singleline.SingleLineProtectionType
import ru.mugalimov.volthome.domain.use_case.EstimatePanelEquipmentCostUseCase
import ru.mugalimov.volthome.domain.use_case.EstimateProtectionCostUseCase
import ru.mugalimov.volthome.domain.use_case.GenerateSingleLineDiagramUseCase
import ru.mugalimov.volthome.ui.screens.explication.export_pdf.SingleLineDiagramRenderer

class PanelEngineeringIntegrationTest {
    private val protectionPrice = MoneyRange(10_000, 20_000, 30_000)
    private val baseEstimate = EstimateProtectionCostUseCase(
        object : ProtectionPriceCatalogProvider {
            override fun get() = ProtectionPriceCatalog(
                updatedAt = "2026-08-21",
                entries = ProtectionDeviceKind.entries.flatMap { kind ->
                    (1..4).map { poles ->
                        ProtectionPriceEntry(kind, poles, 1, 160, price = protectionPrice)
                    }
                }
            )
        }
    )

    @Test
    fun `unified estimate includes calculated and manually added apparatus`() {
        val custom = auxiliary(
            kind = AuxiliaryApparatusKind.VOLTAGE_RELAY,
            point = AuxiliaryConnectionPoint.AFTER_INCOMER,
            price = 345_600
        )

        val estimate = EstimatePanelEquipmentCostUseCase(baseEstimate)(
            incomer = incomer(),
            groups = listOf(group()),
            customModules = listOf(custom)
        )

        assertEquals(1, estimate.auxiliaryLines.size)
        assertEquals(
            estimate.protection.total.typicalKopecks + 345_600,
            estimate.total.typicalKopecks
        )
        assertEquals(estimate.total.typicalKopecks, estimate.unifiedProtectionView.total.typicalKopecks)
    }

    @Test
    fun `all auxiliary apparatus kinds are exported and unassigned one is warned`() {
        val modules = AuxiliaryApparatusKind.entries.mapIndexed { index, kind ->
            auxiliary(
                kind = kind,
                point = if (index == 0) AuxiliaryConnectionPoint.UNASSIGNED else AuxiliaryConnectionPoint.AFTER_INCOMER,
                id = "aux-$index"
            )
        }

        val diagram = GenerateSingleLineDiagramUseCase()(
            projectName = "Test",
            phaseMode = PhaseMode.THREE,
            groups = listOf(group()),
            incomer = incomer(),
            totalInstalledPowerWatts = 1_800.0,
            totalCalculatedPowerWatts = 1_200.0,
            totalCurrentAmps = 8.0,
            customModules = modules,
            generatedAtMillis = 1L
        )

        val exportedTypes = (diagram.protectionBlocks + diagram.unassignedProtectionBlocks).map { it.type }.toSet()
        assertTrue(SingleLineProtectionType.VOLTAGE_RELAY in exportedTypes)
        assertTrue(SingleLineProtectionType.PHASE_CONTROL_RELAY in exportedTypes)
        assertTrue(SingleLineProtectionType.CURRENT_RELAY in exportedTypes)
        assertTrue(SingleLineProtectionType.MODULAR_CONTACTOR in exportedTypes)
        assertTrue(SingleLineProtectionType.SURGE_PROTECTION in exportedTypes)
        assertEquals("Точка подключения не задана", diagram.unassignedProtectionBlocks.single().warning)
        val rendered = SingleLineDiagramRenderer.render(diagram)
        assertTrue(rendered.contains("Ручные аппараты без подключения"))
        assertTrue(rendered.contains("Точка подключения не задана"))
    }

    @Test
    fun `single and three phase groups use correct conductor count`() {
        val useCase = GenerateSingleLineDiagramUseCase()
        val onePhase = useCase("1P", PhaseMode.SINGLE, listOf(group(Phase.A)), incomer(), null, null, null)
        val threePhase = useCase("3P", PhaseMode.THREE, listOf(group(Phase.THREE_PHASE)), incomer(), null, null, null)

        assertTrue(onePhase.phaseSections.single().groups.single().cableLabel?.startsWith("3×") == true)
        assertTrue(threePhase.phaseSections.single().groups.single().cableLabel?.startsWith("5×") == true)
    }

    private fun auxiliary(
        kind: AuxiliaryApparatusKind,
        point: AuxiliaryConnectionPoint,
        price: Long = 50_000,
        id: String = "aux-1"
    ) = PanelCustomModuleSnapshot(
        id = id,
        designation = "K-$id",
        apparatus = SelectedAuxiliaryApparatusSnapshot(
            customModuleId = id,
            productId = id,
            manufacturer = "Test",
            series = "Engineering",
            model = kind.name,
            article = id,
            function = kind,
            moduleUnits = 2,
            ratedCurrentA = 40,
            poles = 2,
            summary = kind.name,
            characteristics = emptyList(),
            catalogVersion = "test",
            catalogPrice = MoneyRange(price, price, price),
            priceSource = "test",
            priceUpdatedAt = "2026-08-21",
            connection = AuxiliaryElectricalConnection(point = point, phase = Phase.A),
            addedAtEpochMs = 1
        )
    )

    private fun incomer() = IncomerSpec(
        kind = IncomerKind.MCB_ONLY,
        poles = 2,
        mcbRating = 40,
        mcbCurve = "C",
        icn = 6_000
    )

    private fun group(phase: Phase = Phase.A) = CircuitGroup(
        groupId = 1,
        groupNumber = 1,
        roomName = "Комната",
        roomId = 1,
        groupType = DeviceType.SOCKET,
        devices = emptyList(),
        nominalCurrent = 8.0,
        installedPowerW = 1_800,
        circuitBreaker = 16,
        cableSection = 2.5,
        breakerType = "C",
        rcdRequired = false,
        phase = phase
    )
}
