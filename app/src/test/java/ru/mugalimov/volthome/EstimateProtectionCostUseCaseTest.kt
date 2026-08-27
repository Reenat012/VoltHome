package ru.mugalimov.volthome

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.mugalimov.volthome.domain.model.CalculationSource
import ru.mugalimov.volthome.domain.model.catalog.ApparatusCompatibility
import ru.mugalimov.volthome.domain.model.catalog.ProductStatus
import ru.mugalimov.volthome.domain.model.catalog.SelectedApparatusSnapshot
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.incomer.IncomerKind
import ru.mugalimov.volthome.domain.model.incomer.IncomerSpec
import ru.mugalimov.volthome.domain.model.incomer.RcdSelectivity
import ru.mugalimov.volthome.domain.model.incomer.RcdType
import ru.mugalimov.volthome.domain.model.pricing.MoneyRange
import ru.mugalimov.volthome.domain.model.pricing.ProtectionDeviceKind
import ru.mugalimov.volthome.domain.model.pricing.ProtectionPriceCatalog
import ru.mugalimov.volthome.domain.model.pricing.ProtectionPriceEntry
import ru.mugalimov.volthome.domain.model.protection.RcdKind
import ru.mugalimov.volthome.domain.model.protection.RcdSpec
import ru.mugalimov.volthome.domain.model.provider.ProtectionPriceCatalogProvider
import ru.mugalimov.volthome.domain.use_case.EstimateProtectionCostUseCase

class EstimateProtectionCostUseCaseTest {
    private val price = MoneyRange(10_000, 20_000, 30_000)
    private val useCase = EstimateProtectionCostUseCase(
        object : ProtectionPriceCatalogProvider {
            override fun get() = ProtectionPriceCatalog(
                updatedAt = "2026-08-01",
                entries = ProtectionDeviceKind.entries.flatMap { kind ->
                    (1..4).map { poles ->
                        ProtectionPriceEntry(kind, poles, 1, 160, price = price)
                    }
                }
            )
        }
    )

    @Test
    fun `mcb plus rcd incomer is counted as two apparatus`() {
        val result = useCase(incomer(IncomerKind.MCB_PLUS_RCD), emptyList())

        assertEquals(2, result.totalDeviceCount)
        assertEquals(2, result.pricedDeviceCount)
        assertEquals(40_000, result.total.typicalKopecks)
    }

    @Test
    fun `rcbo group is one apparatus and is not double counted as breaker plus rcd`() {
        val result = useCase(
            incomer(IncomerKind.MCB_ONLY),
            listOf(group(rcdKind = RcdKind.RCBO))
        )

        assertEquals(2, result.totalDeviceCount)
        assertEquals(2, result.lines.sumOf { it.quantity })
        assertEquals(1, result.lines.single { it.spec.kind == ProtectionDeviceKind.RCBO }.quantity)
    }

    @Test
    fun `identical group breakers are consolidated into one estimate line`() {
        val result = useCase(
            incomer(IncomerKind.MCB_ONLY),
            listOf(group(1), group(2))
        )

        val breakerLine = result.lines.single {
            it.spec.kind == ProtectionDeviceKind.MCB && it.spec.poles == 1
        }
        assertEquals(2, breakerLine.quantity)
        assertEquals(40_000, breakerLine.totalPrice.typicalKopecks)
    }

    @Test
    fun `10A and 16A breakers use different catalog prices`() {
        val differentiated = EstimateProtectionCostUseCase(
            object : ProtectionPriceCatalogProvider {
                override fun get() = ProtectionPriceCatalog(
                    updatedAt = "2026-08-07",
                    entries = listOf(
                        ProtectionPriceEntry(
                            ProtectionDeviceKind.MCB, 1, 1, 10,
                            price = MoneyRange(30_000, 55_000, 95_000)
                        ),
                        ProtectionPriceEntry(
                            ProtectionDeviceKind.MCB, 1, 11, 16,
                            price = MoneyRange(35_000, 65_000, 110_000)
                        )
                    )
                )
            }
        )

        val result = differentiated(
            incomer(IncomerKind.MCB_ONLY),
            listOf(group(number = 1, breaker = 10), group(number = 2, breaker = 16))
        )

        val price10 = result.lines.single { it.spec.ratedCurrentA == 10 }.unitPrice.typicalKopecks
        val price16 = result.lines.single { it.spec.ratedCurrentA == 16 }.unitPrice.typicalKopecks
        assertEquals(55_000, price10)
        assertEquals(65_000, price16)
    }

    @Test
    fun `selected concrete product and user price replace generic estimate for its slot`() {
        val required = ru.mugalimov.volthome.domain.use_case.ProtectionDeviceInventory
            .forIncomer(incomer(IncomerKind.MCB_ONLY))
            .single()
            .spec
        val selection = snapshot(
            slotId = "incomer-breaker",
            required = required,
            userPriceKopecks = 123_400
        )

        val result = useCase(
            incomer(IncomerKind.MCB_ONLY),
            emptyList(),
            mapOf(selection.slotId to selection)
        )

        assertEquals(1, result.selectedProductCount)
        assertEquals(123_400, result.total.typicalKopecks)
        assertEquals("product-1", result.lines.single().productId)
        assertEquals(true, result.lines.single().isUserPrice)
        assertEquals(listOf("incomer-breaker"), result.lines.single().slotIds)
    }

    @Test
    fun `stale incompatible selection falls back to generic price`() {
        val required = ru.mugalimov.volthome.domain.use_case.ProtectionDeviceInventory
            .forIncomer(incomer(IncomerKind.MCB_ONLY))
            .single()
            .spec
        val selection = snapshot(
            slotId = "incomer-breaker",
            required = required,
            productSpec = required.copy(ratedCurrentA = required.ratedCurrentA + 10)
        )

        val result = useCase(
            incomer(IncomerKind.MCB_ONLY),
            emptyList(),
            mapOf(selection.slotId to selection)
        )

        assertEquals(0, result.selectedProductCount)
        assertEquals(listOf("incomer-breaker"), result.staleSelectionSlotIds)
        assertEquals(20_000, result.total.typicalKopecks)
    }

    private fun snapshot(
        slotId: String,
        required: ru.mugalimov.volthome.domain.model.pricing.ProtectionDeviceSpec,
        productSpec: ru.mugalimov.volthome.domain.model.pricing.ProtectionDeviceSpec = required,
        userPriceKopecks: Long? = null
    ) = SelectedApparatusSnapshot(
        slotId = slotId,
        requiredSpec = required,
        productId = "product-1",
        productSpec = productSpec,
        productStatus = ProductStatus.ACTIVE,
        manufacturer = "IEK",
        series = "Test",
        model = "C16",
        article = "TEST-1",
        catalogVersion = "test-v1",
        moduleUnits = required.poles,
        catalogPrice = MoneyRange(90_000, 100_000, 110_000),
        priceSource = "test",
        priceUpdatedAt = "2026-08-07",
        userPriceKopecks = userPriceKopecks,
        compatibility = ApparatusCompatibility.EXACT,
        compatibilityReasonCodes = emptyList(),
        selectedAtEpochMs = 1L
    )

    private fun incomer(kind: IncomerKind) = IncomerSpec(
        kind = kind,
        poles = 2,
        mcbRating = 40,
        mcbCurve = "C",
        icn = 6_000,
        rcdType = RcdType.A,
        rcdRatedCurrentA = 40,
        rcdSensitivityMa = 300,
        rcdSelectivity = RcdSelectivity.S
    )

    private fun group(
        number: Int = 1,
        rcdKind: RcdKind? = null,
        breaker: Int = 16
    ) = CircuitGroup(
        groupNumber = number,
        roomName = "Комната",
        roomId = 1,
        groupType = DeviceType.SOCKET,
        devices = emptyList(),
        nominalCurrent = 8.0,
        installedPowerW = 1_800,
        circuitBreaker = breaker,
        cableSection = 2.5,
        breakerType = "C",
        rcdRequired = rcdKind != null,
        rcdSpec = rcdKind?.let {
            RcdSpec(
                kind = it,
                ratedCurrentA = breaker,
                leakageCurrentMa = 30,
                type = RcdType.A,
                poles = 2,
                selectivity = RcdSelectivity.NONE,
                source = CalculationSource.AUTO
            )
        },
        phase = Phase.A
    )
}
