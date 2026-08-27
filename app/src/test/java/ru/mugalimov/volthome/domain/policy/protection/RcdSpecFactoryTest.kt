package ru.mugalimov.volthome.domain.policy.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.mugalimov.volthome.domain.model.CalculationSource
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.incomer.RcdType

class RcdSpecFactoryTest {

    @Test
    fun `one phase group receives two pole type A rcd`() {
        val spec = RcdSpecFactory.createGroupRecommendation(
            required = true,
            leakageCurrentMa = 30,
            breakerRatingA = 16,
            phase = Phase.A,
            source = CalculationSource.AUTO
        )!!
        assertEquals(16, spec.ratedCurrentA)
        assertEquals(2, spec.poles)
        assertEquals(30, spec.leakageCurrentMa)
        assertEquals(RcdType.A, spec.type)
    }

    @Test
    fun `three phase group receives four pole rcd`() {
        val spec = RcdSpecFactory.createGroupRecommendation(
            required = true,
            leakageCurrentMa = 30,
            breakerRatingA = 25,
            phase = Phase.THREE_PHASE,
            source = CalculationSource.AUTO
        )!!
        assertEquals(4, spec.poles)
        assertEquals(25, spec.ratedCurrentA)
    }

    @Test
    fun `unprotected group has no spec`() {
        assertNull(
            RcdSpecFactory.createGroupRecommendation(
                required = false,
                leakageCurrentMa = null,
                breakerRatingA = 10,
                phase = Phase.A,
                source = CalculationSource.AUTO
            )
        )
    }
}
