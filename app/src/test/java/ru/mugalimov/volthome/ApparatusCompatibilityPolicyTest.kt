package ru.mugalimov.volthome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mugalimov.volthome.domain.model.catalog.ApparatusCompatibility
import ru.mugalimov.volthome.domain.model.catalog.ApparatusCompatibilityPolicy
import ru.mugalimov.volthome.domain.model.catalog.ApparatusProduct
import ru.mugalimov.volthome.domain.model.catalog.CompatibilityReasonCode
import ru.mugalimov.volthome.domain.model.catalog.ProductPrice
import ru.mugalimov.volthome.domain.model.pricing.MoneyRange
import ru.mugalimov.volthome.domain.model.pricing.ProtectionDeviceKind
import ru.mugalimov.volthome.domain.model.pricing.ProtectionDeviceSpec

class ApparatusCompatibilityPolicyTest {

    @Test
    fun `larger breaker rating is not accepted for calculated line`() {
        val required = mcb(16)
        val result = ApparatusCompatibilityPolicy.evaluate(required, product(mcb(20)))

        assertEquals(ApparatusCompatibility.INCOMPATIBLE, result.result)
        assertTrue(CompatibilityReasonCode.RATED_CURRENT_MISMATCH in result.reasonCodes)
    }

    @Test
    fun `rcd with larger rated current remains compatible`() {
        val required = ProtectionDeviceSpec(ProtectionDeviceKind.RCD, 2, 40, leakageCurrentMa = 30)
        val offered = required.copy(ratedCurrentA = 63)
        val result = ApparatusCompatibilityPolicy.evaluate(required, product(offered))

        assertEquals(ApparatusCompatibility.COMPATIBLE_WITH_DIFFERENCES, result.result)
        assertTrue(result.reasonCodes.isEmpty())
    }

    @Test
    fun `higher breaking capacity remains compatible`() {
        val required = mcb(16).copy(breakingCapacityA = 6_000)
        val offered = required.copy(breakingCapacityA = 10_000)

        assertEquals(
            ApparatusCompatibility.COMPATIBLE_WITH_DIFFERENCES,
            ApparatusCompatibilityPolicy.evaluate(required, product(offered)).result
        )
    }

    private fun mcb(rating: Int) = ProtectionDeviceSpec(
        kind = ProtectionDeviceKind.MCB,
        poles = 1,
        ratedCurrentA = rating,
        breakerCurve = "C"
    )

    private fun product(spec: ProtectionDeviceSpec) = ApparatusProduct(
        productId = "test",
        manufacturer = "Test",
        series = "Series",
        model = "Model",
        article = "A-1",
        function = spec.kind,
        spec = spec,
        moduleUnits = spec.poles,
        price = ProductPrice(MoneyRange(100, 200, 300), "test", "2026-08-07")
    )
}
