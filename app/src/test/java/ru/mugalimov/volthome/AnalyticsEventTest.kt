package ru.mugalimov.volthome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import ru.mugalimov.volthome.core.analytics.AnalyticsEvent
import ru.mugalimov.volthome.core.analytics.AnalyticsMode
import ru.mugalimov.volthome.core.analytics.PaywallSource
import ru.mugalimov.volthome.core.analytics.toReport

class AnalyticsEventTest {

    @Test
    fun `project event uses stable automatic mode`() {
        val report = AnalyticsEvent.ProjectCreated(AnalyticsMode.AUTOMATIC).toReport()

        assertEquals("project_created", report.name)
        assertEquals(mapOf("mode" to "automatic"), report.parameters)
    }

    @Test
    fun `calculation event omits phase count when it is not known`() {
        val report = AnalyticsEvent.CalculationCompleted(linesCount = 7).toReport()

        assertEquals("calculation_completed", report.name)
        assertEquals(7, report.parameters["lines_count"])
        assertFalse(report.parameters.containsKey("phase_count"))
    }

    @Test
    fun `board preview contains only engineering counters`() {
        val report = AnalyticsEvent.BoardPreviewShown(
            devicesCount = 12,
            dinRailsCount = 3,
            modulesUsed = 28,
            modulesTotal = 36
        ).toReport()

        assertEquals("board_preview_shown", report.name)
        assertEquals(12, report.parameters["devices_count"])
        assertEquals(3, report.parameters["din_rails_count"])
        assertEquals(28, report.parameters["modules_used"])
        assertEquals(36, report.parameters["modules_total"])
    }

    @Test
    fun `purchase events keep source and product id`() {
        val started = AnalyticsEvent.PurchaseStarted(
            source = PaywallSource.MANUAL_BOARD,
            productId = "volthome_pro"
        ).toReport()
        val success = AnalyticsEvent.PurchaseSuccess(
            source = PaywallSource.MANUAL_BOARD,
            productId = "volthome_pro"
        ).toReport()

        assertEquals("purchase_started", started.name)
        assertEquals("purchase_success", success.name)
        assertEquals("manual_board", started.parameters["source"])
        assertEquals("volthome_pro", success.parameters["product_id"])
    }

    @Test
    fun `board opened distinguishes automatic and manual modes`() {
        val automatic = AnalyticsEvent.BoardOpened(AnalyticsMode.AUTOMATIC).toReport()
        val manual = AnalyticsEvent.BoardOpened(AnalyticsMode.MANUAL).toReport()

        assertEquals(mapOf("mode" to "automatic"), automatic.parameters)
        assertEquals(mapOf("mode" to "manual"), manual.parameters)
    }

    @Test
    fun `cable calculation paywall uses stable source`() {
        val report = AnalyticsEvent.PaywallShown(PaywallSource.CABLE_CALCULATION).toReport()

        assertEquals("paywall_shown", report.name)
        assertEquals("cable_calculation", report.parameters["source"])
    }
}
