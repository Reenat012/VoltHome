package ru.mugalimov.volthome.core.analytics

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Короткоживущий контекст пути в PRO. Персональные и проектные данные здесь не хранятся.
 */
@Singleton
class PurchaseAnalyticsContext @Inject constructor() {

    private data class State(
        val source: PaywallSource,
        val paywallShown: Boolean
    )

    private var state: State? = null

    @Synchronized
    fun begin(source: PaywallSource, paywallAlreadyShown: Boolean = false) {
        state = State(source = source, paywallShown = paywallAlreadyShown)
    }

    @Synchronized
    fun markPaywallShownIfNeeded(
        fallbackSource: PaywallSource = PaywallSource.PRO_SCREEN
    ): PaywallSource? {
        val current = state ?: State(source = fallbackSource, paywallShown = false)
        if (current.paywallShown) return null
        state = current.copy(paywallShown = true)
        return current.source
    }

    @Synchronized
    fun currentSource(): PaywallSource? = state?.source

    @Synchronized
    fun clear() {
        state = null
    }
}
