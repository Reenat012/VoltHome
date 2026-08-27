package ru.mugalimov.volthome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.mugalimov.volthome.core.analytics.PaywallSource
import ru.mugalimov.volthome.core.analytics.PurchaseAnalyticsContext

class PurchaseAnalyticsContextTest {

    @Test
    fun `paywall is marked only once for one purchase path`() {
        val context = PurchaseAnalyticsContext()
        context.begin(PaywallSource.BOARD_PREVIEW)

        assertEquals(
            PaywallSource.BOARD_PREVIEW,
            context.markPaywallShownIfNeeded()
        )
        assertNull(context.markPaywallShownIfNeeded())
        assertEquals(PaywallSource.BOARD_PREVIEW, context.currentSource())
    }

    @Test
    fun `new path can show a new paywall`() {
        val context = PurchaseAnalyticsContext()
        context.begin(PaywallSource.BOARD_PREVIEW, paywallAlreadyShown = true)
        assertNull(context.markPaywallShownIfNeeded())

        context.begin(PaywallSource.PRO_MENU)
        assertEquals(PaywallSource.PRO_MENU, context.markPaywallShownIfNeeded())
    }
}
