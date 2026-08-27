package ru.mugalimov.volthome

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mugalimov.volthome.domain.model.UserPlan

class CableLineCalculationAccessTest {

    @Test
    fun `free plan cannot edit full cable calculation`() {
        assertFalse(UserPlan.FREE.capabilities.cableLineCalculation)
    }

    @Test
    fun `pro plan can edit full cable calculation`() {
        val pro = UserPlan(plan = "pro", planUntilEpochSeconds = null)

        assertTrue(pro.capabilities.cableLineCalculation)
    }

    @Test
    fun `expired pro plan cannot edit full cable calculation`() {
        val expiredPro = UserPlan(plan = "pro", planUntilEpochSeconds = 1L)

        assertFalse(expiredPro.capabilities.cableLineCalculation)
    }
}
