package ru.mugalimov.volthome

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mugalimov.volthome.domain.model.UserPlan

class PanelVisualizationAccessTest {

    @Test
    fun `free plan has preview instead of full panel access`() {
        assertFalse(UserPlan.FREE.capabilities.panelVisualization)
    }

    @Test
    fun `pro plan can open panel visualization`() {
        val pro = UserPlan(plan = "pro", planUntilEpochSeconds = null)

        assertTrue(pro.capabilities.panelVisualization)
    }
}
